package com.techshop.controller;

import com.techshop.dao.ProductUnitDao;
import com.techshop.model.Product;
import com.techshop.model.ProductUnit;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.util.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Choix de l'appareil (IMEI / n° de série) à vendre pour un produit suivi par
 * IMEI, avec le choix de la durée de garantie (SPEC §4.3, §6.2). Le stock
 * disponible est listé ; un champ permet de scanner / filtrer un IMEI.
 */
public final class ImeiDialog {

    /** Sélection : identifiant d'unité, IMEI et garantie en mois. */
    public record Choix(Integer unitId, String imei, int warrantyMonths) {
    }

    private ImeiDialog() {
    }

    public static void ouvrir(Product produit, BiConsumer<Choix, Boolean> onChoix) {
        ProductUnitDao dao = new ProductUnitDao();
        List<ProductUnit> charges;
        try {
            charges = dao.listInStock(produit.getId());
        } catch (SQLException e) {
            charges = List.of();
        }
        final List<ProductUnit> disponibles = List.copyOf(charges);
        if (disponibles.isEmpty()) {
            DialogService.get().confirm("Appareil indisponible", "Aucun appareil en stock pour « " + produit.getName() + " ». Réceptionnez le stock ou l'IMEI correspondant.", "Fermer", false).thenAccept(b -> {});
            return;
        }

        ObservableList<ProductUnit> lignes = FXCollections.observableArrayList(disponibles);
        TableView<ProductUnit> table = new TableView<>(lignes);
        table.getStyleClass().add("data-table");
        table.setPrefHeight(260);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(EmptyState.of("smartphone", "Aucun résultat",
                "Aucun IMEI ne correspond au filtre."));

        TableColumn<ProductUnit, String> cImei = new TableColumn<>("IMEI");
        cImei.setMinWidth(180);
        cImei.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().imei()));
        TableColumn<ProductUnit, String> cSerie = new TableColumn<>("N° de série");
        cSerie.setMinWidth(140);
        cSerie.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().serialNumber() == null ? "—" : d.getValue().serialNumber()));
        TableColumn<ProductUnit, String> cReception = new TableColumn<>("Reçu le");
        cReception.setMinWidth(140);
        cReception.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().receivedAt() == null ? "—" : Format.dateTime(d.getValue().receivedAt())));
        table.getColumns().setAll(cImei, cSerie, cReception);
        table.getSelectionModel().selectFirst();

        TextField filtre = new TextField();
        filtre.setPromptText("Scanner ou rechercher un IMEI…");
        filtre.getStyleClass().add("field");
        HBox.setHgrow(filtre, Priority.ALWAYS);
        filtre.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.trim().toLowerCase();
            List<ProductUnit> filtres = disponibles.stream()
                    .filter(u -> q.isEmpty()
                            || (u.imei() != null && u.imei().toLowerCase().contains(q))
                            || (u.serialNumber() != null && u.serialNumber().toLowerCase().contains(q)))
                    .toList();
            lignes.setAll(filtres);
            if (!lignes.isEmpty()) {
                table.getSelectionModel().selectFirst();
            }
        });

        Spinner<Integer> garantie = new Spinner<>(0, 120,
                Math.max(0, produit.getWarrantyMonths()));
        garantie.setEditable(true);
        garantie.getStyleClass().add("field");
        FormField garantieChamp = Fields.of("Garantie (mois)", garantie, null);

        Label info = new Label(produit.getName() + " — " + Format.money(produit.getSalePrice()));
        info.getStyleClass().add("card-title");
        Label note = new Label(disponibles.size() + " appareil(s) en stock pour ce produit.");
        note.getStyleClass().add("muted");

        VBox corps = new VBox(10, info, note, filtre, table, garantieChamp);
        corps.setPrefWidth(560);
        corps.setMaxHeight(520);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.primary("Ajouter au panier", "check");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Choisir l'appareil (IMEI)", "smartphone", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            ProductUnit sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                return;
            }
            int mois = garantie.getValue() == null ? 0 : garantie.getValue();
            handle.close();
            if (onChoix != null) {
                onChoix.accept(new Choix((int) sel.id(), sel.imei(), mois), Boolean.TRUE);
            }
        });
        valider.setDefaultButton(true);
        filtre.setOnAction(e -> {
            if (lignes.size() == 1) {
                valider.fire();
            }
        });
    }
}
