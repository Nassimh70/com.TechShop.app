package com.techshop.controller;

import com.techshop.model.Brand;
import com.techshop.model.Category;
import com.techshop.service.CatalogService;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.PromptDialog;
import com.techshop.ui.components.ToastService;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Gestion des catégories et des marques (SPEC §4.5) : création, renommage et
 * activation/désactivation. Ouvert depuis le module Produits ; les services
 * valident et tracent chaque action.
 */
public final class CatalogDialog {

    private final Runnable onChanged;
    private final CatalogService catalogue = CatalogService.get();

    private final ObservableList<Category> categories = FXCollections.observableArrayList();
    private final ObservableList<Brand> marques = FXCollections.observableArrayList();

    private CatalogDialog(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public static void ouvrir(Runnable onChanged) {
        new CatalogDialog(onChanged).montrer();
    }

    private void montrer() {
        VBox ongletCategories = new VBox(12);
        VBox ongletMarques = new VBox(12);

        TabPane onglets = new TabPane();
        onglets.getStyleClass().add("tabs");
        onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        onglets.setPrefSize(520, 420);
        Tab tCat = new Tab("Catégories", ongletCategories);
        Tab tMar = new Tab("Marques", ongletMarques);
        tCat.setClosable(false);
        tMar.setClosable(false);
        onglets.getTabs().addAll(tCat, tMar);

        construireCategories(ongletCategories);
        construireMarques(ongletMarques);

        Button fermer = Buttons.secondary("Fermer");
        HBox pied = new HBox(fermer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Catégories & marques", "tag", onglets, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);
        fermer.setOnAction(e -> handle.close());

        recharger();
    }

    // --------------------------------------------------------------- catégories

    private TableView<Category> tableCategories;

    private void construireCategories(VBox conteneur) {
        TextField champ = new TextField();
        champ.getStyleClass().add("field");
        champ.setPromptText("Nouvelle catégorie…");
        Button ajouter = Buttons.primary("Ajouter", "plus");
        ajouter.setOnAction(e -> {
            try {
                catalogue.createCategory(champ.getText());
                champ.clear();
                recharger();
                onChanged.run();
                ToastService.get().success("Catégorie", "Catégorie ajoutée.");
            } catch (ValidationException ex) {
                ToastService.get().error("Catégorie", ex.getMessage());
            }
        });
        HBox barre = new HBox(8, champ, ajouter);
        HBox.setHgrow(champ, Priority.ALWAYS);

        tableCategories = new TableView<>(categories);
        tableCategories.getStyleClass().add("data-table");
        tableCategories.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableCategories, Priority.ALWAYS);

        TableColumn<Category, String> cNom = new TableColumn<>("Nom");
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().name()));

        TableColumn<Category, Category> cStatut = new TableColumn<>("Statut");
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(null);
                setGraphic(empty || c == null ? null
                        : Badge.of(c.active() ? "Active" : "Inactive",
                        c.active() ? Badge.BadgeType.SUCCESS : Badge.BadgeType.NEUTRAL));
            }
        });

        TableColumn<Category, Category> cActions = new TableColumn<>("");
        cActions.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(null);
                if (empty || c == null) {
                    setGraphic(null);
                    return;
                }
                Button renommer = Buttons.iconCompact("edit-2", "Renommer");
                renommer.setOnAction(e -> renommerCategorie(c));
                Button basculer = Buttons.iconCompact(
                        c.active() ? "x-circle" : "check-circle",
                        c.active() ? "Désactiver" : "Activer");
                basculer.setOnAction(e -> basculerCategorie(c));
                setGraphic(new HBox(6, renommer, basculer));
            }
        });

        tableCategories.getColumns().addAll(cNom, cStatut, cActions);
        conteneur.getChildren().setAll(barre, tableCategories);
    }

    private void renommerCategorie(Category c) {
        PromptDialog.text("Renommer la catégorie", "edit-2", "Nom", c.name(),
                "Nom de la catégorie", nom -> {
                    catalogue.updateCategory(c.id(), nom, c.active());
                    recharger();
                    onChanged.run();
                    ToastService.get().success("Catégorie", "Catégorie renommée.");
                });
    }

    private void basculerCategorie(Category c) {
        try {
            catalogue.setCategoryActive(c.id(), !c.active());
            recharger();
            onChanged.run();
        } catch (ValidationException ex) {
            ToastService.get().error("Catégorie", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ marques

    private TableView<Brand> tableMarques;

    private void construireMarques(VBox conteneur) {
        TextField champ = new TextField();
        champ.getStyleClass().add("field");
        champ.setPromptText("Nouvelle marque…");
        Button ajouter = Buttons.primary("Ajouter", "plus");
        ajouter.setOnAction(e -> {
            try {
                catalogue.createBrand(champ.getText());
                champ.clear();
                recharger();
                onChanged.run();
                ToastService.get().success("Marque", "Marque ajoutée.");
            } catch (ValidationException ex) {
                ToastService.get().error("Marque", ex.getMessage());
            }
        });
        HBox barre = new HBox(8, champ, ajouter);
        HBox.setHgrow(champ, Priority.ALWAYS);

        tableMarques = new TableView<>(marques);
        tableMarques.getStyleClass().add("data-table");
        tableMarques.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableMarques, Priority.ALWAYS);

        TableColumn<Brand, String> cNom = new TableColumn<>("Nom");
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().name()));

        TableColumn<Brand, Brand> cStatut = new TableColumn<>("Statut");
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Brand b, boolean empty) {
                super.updateItem(b, empty);
                setText(null);
                setGraphic(empty || b == null ? null
                        : Badge.of(b.active() ? "Active" : "Inactive",
                        b.active() ? Badge.BadgeType.SUCCESS : Badge.BadgeType.NEUTRAL));
            }
        });

        TableColumn<Brand, Brand> cActions = new TableColumn<>("");
        cActions.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Brand b, boolean empty) {
                super.updateItem(b, empty);
                setText(null);
                if (empty || b == null) {
                    setGraphic(null);
                    return;
                }
                Button renommer = Buttons.iconCompact("edit-2", "Renommer");
                renommer.setOnAction(e -> renommerMarque(b));
                Button basculer = Buttons.iconCompact(
                        b.active() ? "x-circle" : "check-circle",
                        b.active() ? "Désactiver" : "Activer");
                basculer.setOnAction(e -> basculerMarque(b));
                setGraphic(new HBox(6, renommer, basculer));
            }
        });

        tableMarques.getColumns().addAll(cNom, cStatut, cActions);
        conteneur.getChildren().setAll(barre, tableMarques);
    }

    private void renommerMarque(Brand b) {
        PromptDialog.text("Renommer la marque", "edit-2", "Nom", b.name(),
                "Nom de la marque", nom -> {
                    catalogue.updateBrand(b.id(), nom, b.active());
                    recharger();
                    onChanged.run();
                    ToastService.get().success("Marque", "Marque renommée.");
                });
    }

    private void basculerMarque(Brand b) {
        try {
            catalogue.setBrandActive(b.id(), !b.active());
            recharger();
            onChanged.run();
        } catch (ValidationException ex) {
            ToastService.get().error("Marque", ex.getMessage());
        }
    }

    private void recharger() {
        categories.setAll(catalogue.categories(true));
        marques.setAll(catalogue.brands(true));
    }
}
