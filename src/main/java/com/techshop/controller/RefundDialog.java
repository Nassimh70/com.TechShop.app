package com.techshop.controller;

import com.techshop.model.PaymentMethod;
import com.techshop.model.Sale;
import com.techshop.model.SaleItem;
import com.techshop.service.SaleService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Retour / remboursement partiel ou total d'une vente (SPEC §4.4). Le stock est
 * remis, la dette éventuelle réduite puis le solde remboursé.
 */
public final class RefundDialog {

    private RefundDialog() {
    }

    /** Retour / remboursement d'une vente. */
    public static void ouvrir(Sale vente, Runnable apres) {
        ObservableList<LigneRetour> lignes = FXCollections.observableArrayList();
        for (SaleItem it : vente.getItems()) {
            if (it.returnableQuantity() > 0) {
                lignes.add(new LigneRetour(it));
            }
        }
        if (lignes.isEmpty()) {
            ToastService.get().info("Retour", "Tous les articles de cette vente ont déjà été retournés.");
            return;
        }

        TableView<LigneRetour> table = new TableView<>(lignes);
        table.getStyleClass().add("data-table");
        table.setPrefHeight(220);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<LigneRetour, String> cProd = new TableColumn<>("Produit");
        cProd.setMinWidth(200);
        cProd.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().item.getProductName()));
        TableColumn<LigneRetour, Integer> cVendu = new TableColumn<>("Vendu");
        cVendu.setMinWidth(70);
        cVendu.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().item.getQuantity()));
        TableColumn<LigneRetour, Integer> cDeja = new TableColumn<>("Déjà retourné");
        cDeja.setMinWidth(110);
        cDeja.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().item.getReturnedQuantity()));
        TableColumn<LigneRetour, LigneRetour> cQte = new TableColumn<>("À retourner");
        cQte.setMinWidth(120);
        cQte.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cQte.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LigneRetour l, boolean empty) {
                super.updateItem(l, empty);
                setText(null);
                if (empty || l == null) {
                    setGraphic(null);
                    return;
                }
                Spinner<Integer> sp = new Spinner<>(0, l.item.returnableQuantity(), l.quantite);
                sp.setEditable(true);
                sp.setPrefWidth(90);
                sp.valueProperty().addListener((o, a, b) -> l.quantite = b == null ? 0 : b);
                setGraphic(sp);
            }
        });
        TableColumn<LigneRetour, String> cPrix = new TableColumn<>("P.U.");
        cPrix.setMinWidth(90);
        cPrix.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().item.getUnitPrice())));
        table.getColumns().setAll(cProd, cVendu, cDeja, cQte, cPrix);

        FormField motif = Fields.text("Motif du retour", "Ex. article défectueux, erreur de vente…");
        ComboBox<String> mode = new ComboBox<>();
        mode.getStyleClass().add("field");
        mode.getItems().setAll(PaymentMethod.ESPECES.getLabel(), PaymentMethod.CARTE.getLabel(),
                PaymentMethod.VIREMENT.getLabel());
        mode.getSelectionModel().selectFirst();
        mode.setMaxWidth(Double.MAX_VALUE);
        VBox modeBox = new VBox(6, libelle("Mode de remboursement"), mode);

        VBox corps = new VBox(10, table, motif, modeBox);
        corps.setPrefWidth(620);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.danger("Valider le retour", "corner-down-left");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Retour / remboursement — " + vente.getNumber(),
                "corner-down-left", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            try {
                List<SaleService.RefundLine> refs = new ArrayList<>();
                for (LigneRetour l : lignes) {
                    if (l.quantite > 0) {
                        refs.add(new SaleService.RefundLine(l.item.getId(), l.quantite));
                    }
                }
                if (refs.isEmpty()) {
                    motif.setError("Sélectionnez au moins un article à retourner.");
                    return;
                }
                String methode = switch (mode.getSelectionModel().getSelectedIndex()) {
                    case 1 -> PaymentMethod.CARTE.name();
                    case 2 -> PaymentMethod.VIREMENT.name();
                    default -> PaymentMethod.ESPECES.name();
                };
                var retour = SaleService.get().refund(vente.getId(), refs, motif.textValue(), methode);
                ToastService.get().success("Retour enregistré",
                        retour.getNumber() + " — " + Format.money(retour.getTotal()));
                handle.close();
                if (apres != null) {
                    apres.run();
                }
            } catch (ValidationException ex) {
                motif.setError(ex.getMessage());
            }
        });
        valider.setDefaultButton(true);
    }

    private static Label libelle(String texte) {
        Label l = new Label(texte.toUpperCase());
        l.getStyleClass().add("field-label");
        return l;
    }

    private static final class LigneRetour {
        final SaleItem item;
        int quantite;

        LigneRetour(SaleItem item) {
            this.item = item;
            this.quantite = item.returnableQuantity();
        }
    }
}
