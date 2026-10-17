package com.techshop.controller;

import com.techshop.dao.ProductDao;
import com.techshop.model.Product;
import com.techshop.service.RepairService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Sélecteur de pièce : recherche dans le catalogue (stock disponible affiché),
 * choix de la quantité et du prix de vente appliqué, puis sortie de stock via
 * {@link RepairService#addPart}.
 */
public final class RepairPartPickerDialog {

    private RepairPartPickerDialog() {
    }

    public static void ouvrir(int repairId, Runnable onDone) {
        ProductDao dao = new ProductDao();
        ObservableList<Product> produits = FXCollections.observableArrayList();
        try {
            produits.addAll(dao.search(ProductDao.Filter.all(), "name", true, 300, 0));
        } catch (SQLException ignored) {
        }

        TableView<Product> table = new TableView<>(produits);
        table.getStyleClass().add("data-table");
        table.setPrefHeight(280);
        table.setPlaceholder(EmptyState.of("package", "Aucun produit", "Catalogue vide."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> cRef = new TableColumn<>("Réf.");
        cRef.setMinWidth(90);
        cRef.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getRef()));
        TableColumn<Product, String> cNom = new TableColumn<>("Désignation");
        cNom.setMinWidth(230);
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getName()));
        TableColumn<Product, String> cStock = new TableColumn<>("Stock");
        cStock.setMinWidth(70);
        cStock.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(String.valueOf(d.getValue().getQuantity())));
        TableColumn<Product, String> cPrix = new TableColumn<>("Prix");
        cPrix.setMinWidth(100);
        cPrix.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getSalePrice())));
        table.getColumns().setAll(cRef, cNom, cStock, cPrix);

        TextField rech = new TextField();
        rech.setPromptText("Rechercher une pièce (réf., nom)…");
        rech.getStyleClass().add("field");
        HBox.setHgrow(rech, Priority.ALWAYS);
        rech.textProperty().addListener((o, a, b) -> {
            try {
                String q = b == null || b.isBlank() ? null : b.trim();
                produits.setAll(dao.search(new ProductDao.Filter(q, null, null, null, Boolean.TRUE),
                        "name", true, 300, 0));
            } catch (SQLException ignored) {
            }
        });

        FormField quantite = Fields.number("Quantité", "1");
        quantite.setValue("1");
        FormField prix = Fields.number("Prix de vente appliqué", "Prix du catalogue par défaut");
        Label dispo = new Label("Sélectionnez une pièce pour voir le stock disponible.");
        dispo.getStyleClass().add("muted");

        table.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> {
            if (p != null) {
                prix.setValue(Format.moneyShort(p.getSalePrice()).replace("\u00A0", ""));
                dispo.setText("Stock disponible : " + p.getQuantity() + " — coût moyen "
                        + Format.money(p.getAvgCost() == null ? p.getPurchasePrice() : p.getAvgCost()));
            }
        });

        VBox corps = new VBox(10, rech, table, dispo, new HBox(10, quantite, prix));
        corps.setPrefWidth(640);

        Button annuler = Buttons.secondary("Annuler");
        Button ajouter = Buttons.primary("Ajouter la pièce", "plus");
        HBox pied = new HBox(8, annuler, ajouter);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Ajouter une pièce", "package", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        ajouter.setOnAction(e -> {
            Product p = table.getSelectionModel().getSelectedItem();
            if (p == null) {
                ToastService.get().warning("Pièce", "Sélectionnez une pièce dans la liste.");
                return;
            }
            try {
                int qte = Numbers.entier(quantite.textValue(), 1);
                BigDecimal pu = Numbers.money(prix.textValue());
                RepairService.get().addPart(repairId, p.getId(), qte, pu, null);
                handle.close();
                ToastService.get().success("Pièce ajoutée", p.getName() + " × " + qte);
                if (onDone != null) {
                    onDone.run();
                }
            } catch (ValidationException ex) {
                ToastService.get().error("Stock", ex.getMessage());
            } catch (RuntimeException ex) {
                ToastService.get().error("Pièce", ex.getMessage());
            }
        });
        ajouter.setDefaultButton(true);
    }
}
