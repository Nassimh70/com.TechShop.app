package com.techshop.controller;

import com.techshop.dao.StockMovementDao;
import com.techshop.dao.ProductUnitDao;
import com.techshop.model.Product;
import com.techshop.model.ProductUnit;
import com.techshop.model.StockMovement;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.service.ProductService;
import com.techshop.service.StockService;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.PromptDialog;
import com.techshop.ui.components.StatCard;
import com.techshop.ui.components.TableFooter;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module Stock (SPEC §4.6, §6.2) : historique des mouvements, inventaire
 * physique et appareils suivis par IMEI. Toute variation passe par le
 * {@link StockService}, qui garantit la cohérence et la traçabilité.
 */
public class StockController {

    private static final int TAILLE_MOUVEMENTS = 30;

    private static final List<String> TYPES = List.of(
            "Tous les types", "Entrée", "Sortie", "Ajustement", "Perte",
            "Vente", "Retour", "Réparation", "Inventaire");
    private static final String[] CODES_TYPES = {
            null, "ENTREE", "SORTIE", "AJUSTEMENT", "PERTE",
            "VENTE", "RETOUR", "REPARATION", "INVENTAIRE"};

    private static final List<String> STATUTS_APPAREIL = List.of(
            ProductUnit.EN_STOCK, ProductUnit.VENDU, ProductUnit.EN_REPARATION,
            ProductUnit.RETOURNE, ProductUnit.PERDU);

    @FXML
    private VBox root;
    @FXML
    private HBox headerActions;

    private final StockService stock = StockService.get();
    private final ProductService produits = ProductService.get();
    private final PermissionService permissions = PermissionService.get();

    // --- cartes ---
    private StatCard carteValeur;
    private StatCard carteReferences;
    private StatCard carteFaible;
    private StatCard carteRupture;

    // --- mouvements ---
    private final ObservableList<StockMovement> mouvements = FXCollections.observableArrayList();
    private TableView<StockMovement> tableMouvements;
    private ComboBox<String> filtreType;
    private TextField filtreTexte;
    private DatePicker filtreDu;
    private DatePicker filtreAu;
    private TableFooter piedMouvements;
    private int pageMouvements = 1;

    // --- inventaire ---
    private final ObservableList<Product> inventaire = FXCollections.observableArrayList();
    private final Map<Integer, Integer> comptes = new HashMap<>();
    private TableView<Product> tableInventaire;
    private TextField rechercheInventaire;

    // --- appareils ---
    private final ObservableList<ProductUnit> appareils = FXCollections.observableArrayList();
    private TableView<ProductUnit> tableAppareils;
    private ComboBox<String> filtreStatutAppareil;
    private TextField rechercheAppareil;

    @FXML
    private void initialize() {
        construireActions();
        HBox cartes = construireCartes();

        TabPane onglets = new TabPane();
        onglets.getStyleClass().add("tabs");
        onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        onglets.getTabs().addAll(
                onglet("Mouvements", construireOngletMouvements()),
                onglet("Inventaire", construireOngletInventaire()),
                onglet("Appareils (IMEI)", construireOngletAppareils()));
        VBox.setVgrow(onglets, Priority.ALWAYS);

        root.getChildren().setAll(cartes, onglets);

        chargerMouvements();
        chargerInventaire();
        chargerAppareils();
        majCartes();
    }

    // ---------------------------------------------------------------- actions

    private void construireActions() {
        if (peut(PermissionService.Action.CREATE)) {
            Button entree = Buttons.primary("Entrée de stock", "download");
            entree.setOnAction(e -> StockDialog.ouvrir(StockDialog.Operation.ENTREE, null, this::toutRecharger));
            headerActions.getChildren().add(entree);
        }
        if (peut(PermissionService.Action.EDIT)) {
            Button sortie = Buttons.outline("Sortie", "upload");
            sortie.setOnAction(e -> StockDialog.ouvrir(StockDialog.Operation.SORTIE, null, this::toutRecharger));
            headerActions.getChildren().add(sortie);

            Button perte = Buttons.outline("Perte / casse", "alert-triangle");
            perte.setOnAction(e -> StockDialog.ouvrir(StockDialog.Operation.PERTE, null, this::toutRecharger));
            headerActions.getChildren().add(perte);

            Button ajuster = Buttons.outline("Ajuster", "sliders");
            ajuster.setOnAction(e -> StockDialog.ouvrir(StockDialog.Operation.AJUSTEMENT, null, this::toutRecharger));
            headerActions.getChildren().add(ajuster);
        }
    }

    private HBox construireCartes() {
        carteValeur = new StatCard("Valeur du stock", "0");
        carteReferences = new StatCard("Références actives", "0");
        carteFaible = new StatCard("Stock faible", "0");
        carteRupture = new StatCard("Rupture", "0");
        for (StatCard c : List.of(carteValeur, carteReferences, carteFaible, carteRupture)) {
            c.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(c, Priority.ALWAYS);
        }
        return new HBox(12, carteValeur, carteReferences, carteFaible, carteRupture);
    }

    // -------------------------------------------------------------- mouvements

    private VBox construireOngletMouvements() {
        filtreType = new ComboBox<>(FXCollections.observableArrayList(TYPES));
        filtreType.getStyleClass().add("field");
        filtreType.getSelectionModel().selectFirst();
        filtreType.setOnAction(e -> {
            pageMouvements = 1;
            chargerMouvements();
        });

        FormField champ = Fields.search("Rechercher un produit, un motif…");
        filtreTexte = (TextField) champ.field();
        filtreTexte.setPrefWidth(260);
        filtreTexte.textProperty().addListener((o, a, b) -> {
            pageMouvements = 1;
            chargerMouvements();
        });

        filtreDu = new DatePicker();
        filtreDu.getStyleClass().add("field");
        filtreDu.setPromptText("Du");
        filtreDu.setOnAction(e -> {
            pageMouvements = 1;
            chargerMouvements();
        });
        filtreAu = new DatePicker();
        filtreAu.getStyleClass().add("field");
        filtreAu.setPromptText("Au");
        filtreAu.setOnAction(e -> {
            pageMouvements = 1;
            chargerMouvements();
        });

        Button reinitialiser = Buttons.ghost("Réinitialiser", "x");
        reinitialiser.setOnAction(e -> {
            filtreType.getSelectionModel().selectFirst();
            filtreTexte.clear();
            filtreDu.setValue(null);
            filtreAu.setValue(null);
            pageMouvements = 1;
            chargerMouvements();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox barre = new HBox(10, filtreType, filtreTexte, filtreDu, filtreAu, spacer, reinitialiser);
        barre.setAlignment(Pos.CENTER_LEFT);

        tableMouvements = new TableView<>(mouvements);
        tableMouvements.getStyleClass().add("data-table");
        tableMouvements.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableMouvements, Priority.ALWAYS);
        construireColonnesMouvements();

        piedMouvements = new TableFooter();
        piedMouvements.setPrevAction(() -> {
            if (pageMouvements > 1) {
                pageMouvements--;
                chargerMouvements();
            }
        });
        piedMouvements.setNextAction(() -> {
            pageMouvements++;
            chargerMouvements();
        });

        VBox carte = new VBox(tableMouvements, piedMouvements);
        carte.getStyleClass().add("table-wrap");
        VBox.setVgrow(carte, Priority.ALWAYS);

        VBox contenu = new VBox(12, barre, carte);
        contenu.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));
        return contenu;
    }

    private void construireColonnesMouvements() {
        TableColumn<StockMovement, String> cDate = colonne("Date",
                m -> Format.dateTime(m.movementDate()));
        cDate.setPrefWidth(140);

        TableColumn<StockMovement, StockMovement> cProduit = new TableColumn<>("Produit");
        cProduit.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cProduit.setMinWidth(200);
        cProduit.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StockMovement m, boolean empty) {
                super.updateItem(m, empty);
                setText(null);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                Label nom = new Label(m.productName());
                nom.getStyleClass().add("user-fullname");
                Label ref = new Label(m.productRef());
                ref.getStyleClass().add("muted");
                setGraphic(new VBox(0, nom, ref));
            }
        });

        TableColumn<StockMovement, StockMovement> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StockMovement m, boolean empty) {
                super.updateItem(m, empty);
                setText(null);
                setGraphic(empty || m == null ? null
                        : Badge.of(m.typeLabel(), badgeType(m.type())));
            }
        });

        TableColumn<StockMovement, StockMovement> cQuantite = new TableColumn<>("Quantité");
        cQuantite.setPrefWidth(110);
        cQuantite.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cQuantite.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StockMovement m, boolean empty) {
                super.updateItem(m, empty);
                setText(null);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                Label l = new Label((m.quantity() > 0 ? "+" : "") + m.quantity());
                l.getStyleClass().add(m.quantity() >= 0 ? "money-pos" : "money-neg");
                setGraphic(l);
            }
        });

        TableColumn<StockMovement, StockMovement> cStock = new TableColumn<>("Stock");
        cStock.setPrefWidth(120);
        cStock.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StockMovement m, boolean empty) {
                super.updateItem(m, empty);
                setText(null);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                Label l = new Label(m.quantityBefore() + " → " + m.quantityAfter());
                l.getStyleClass().add("muted");
                setGraphic(l);
            }
        });

        TableColumn<StockMovement, String> cMotif = colonne("Motif",
                m -> m.reason() == null ? "—" : m.reason());
        TableColumn<StockMovement, String> cUtilisateur = colonne("Utilisateur",
                m -> m.username() == null ? "—" : m.username());
        cUtilisateur.setPrefWidth(120);

        tableMouvements.getColumns().setAll(cDate, cProduit, cType, cQuantite, cStock,
                cMotif, cUtilisateur);
    }

    private void chargerMouvements() {
        try {
            StockMovementDao.Filter filtre = new StockMovementDao.Filter(
                    null, codeType(), null, texteOuNull(filtreTexte), du(), au());
            StockService.History historique = stock.history(filtre, pageMouvements, TAILLE_MOUVEMENTS);
            if (pageMouvements > historique.pageCount()) {
                pageMouvements = historique.pageCount();
                historique = stock.history(filtre, pageMouvements, TAILLE_MOUVEMENTS);
            }
            mouvements.setAll(historique.items());
            piedMouvements.setResultCount((int) historique.total());
            piedMouvements.setPage(historique.page(), historique.pageCount());
        } catch (ValidationException ex) {
            ToastService.get().error("Stock", ex.getMessage());
        }
    }

    // -------------------------------------------------------------- inventaire

    private VBox construireOngletInventaire() {
        rechercheInventaire = new TextField();
        rechercheInventaire.getStyleClass().add("field");
        rechercheInventaire.setPromptText("Filtrer les produits à inventorier…");
        rechercheInventaire.setPrefWidth(320);
        rechercheInventaire.textProperty().addListener((o, a, b) -> filtrerInventaire());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button rafraichir = Buttons.outline("Rafraîchir", "refresh-cw");
        rafraichir.setOnAction(e -> chargerInventaire());

        Button valider = Buttons.primary("Valider l'inventaire", "check");
        valider.setDisable(!permissions.can(PermissionDefaults.M_STOCK, PermissionService.Action.VALIDATE));
        valider.setOnAction(e -> validerInventaire());

        HBox barre = new HBox(10, rechercheInventaire, spacer, rafraichir, valider);
        barre.setAlignment(Pos.CENTER_LEFT);

        tableInventaire = new TableView<>(inventaire);
        tableInventaire.getStyleClass().add("data-table");
        tableInventaire.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableInventaire, Priority.ALWAYS);
        construireColonnesInventaire();

        VBox carte = new VBox(tableInventaire);
        carte.getStyleClass().add("table-wrap");
        VBox.setVgrow(carte, Priority.ALWAYS);

        VBox contenu = new VBox(12, barre, carte);
        contenu.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));
        return contenu;
    }

    private void construireColonnesInventaire() {
        TableColumn<Product, Product> cProduit = new TableColumn<>("Produit");
        cProduit.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cProduit.setMinWidth(220);
        cProduit.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                Label nom = new Label(p.getName());
                nom.getStyleClass().add("user-fullname");
                Label ref = new Label(p.getRef() + (p.getCategoryName() == null ? ""
                        : "  •  " + p.getCategoryName()));
                ref.getStyleClass().add("muted");
                setGraphic(new VBox(0, nom, ref));
            }
        });

        TableColumn<Product, String> cSysteme = new TableColumn<>("Stock système");
        cSysteme.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                String.valueOf(d.getValue().getQuantity())));
        cSysteme.setPrefWidth(130);

        TableColumn<Product, Product> cCompte = new TableColumn<>("Comptée");
        cCompte.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cCompte.setPrefWidth(130);
        cCompte.setCellFactory(col -> new TableCell<>() {
            private final TextField champ = new TextField();

            {
                champ.getStyleClass().add("field");
                champ.setPrefWidth(90);
                champ.textProperty().addListener((o, a, v) -> {
                    if (v != null && !v.matches("\\d*")) {
                        champ.setText(v.replaceAll("\\D", ""));
                    }
                });
                champ.setOnAction(e -> commit());
                champ.focusedProperty().addListener((o, a, focus) -> {
                    if (!focus) {
                        commit();
                    }
                });
            }

            private void commit() {
                Product p = getItem();
                if (p == null) {
                    return;
                }
                String t = champ.getText();
                comptes.put(p.getId(), t == null || t.isBlank() ? p.getQuantity() : Integer.parseInt(t));
                tableInventaire.refresh();
            }

            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                champ.setText(String.valueOf(comptes.getOrDefault(p.getId(), p.getQuantity())));
                setGraphic(champ);
            }
        });

        TableColumn<Product, Product> cEcart = new TableColumn<>("Écart");
        cEcart.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cEcart.setPrefWidth(100);
        cEcart.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                int compte = comptes.getOrDefault(p.getId(), p.getQuantity());
                int ecart = compte - p.getQuantity();
                Label l = new Label(ecart > 0 ? "+" + ecart : String.valueOf(ecart));
                l.getStyleClass().add(ecart == 0 ? "muted" : ecart > 0 ? "money-pos" : "money-neg");
                setGraphic(l);
            }
        });

        tableInventaire.getColumns().setAll(cProduit, cSysteme, cCompte, cEcart);
    }

    private void chargerInventaire() {
        comptes.clear();
        inventaire.setAll(produits.allActive());
        filtrerInventaire();
    }

    private void filtrerInventaire() {
        // Filtre visuel simple : on garde la liste complète et on applique un filtre rapide.
        String q = Format.normalize(rechercheInventaire == null ? null : rechercheInventaire.getText());
        if (q.isEmpty()) {
            inventaire.setAll(produits.allActive());
        } else {
            List<Product> filtres = new ArrayList<>();
            for (Product p : produits.allActive()) {
                if (Format.normalize(p.getName()).contains(q)
                        || Format.normalize(p.getRef()).contains(q)) {
                    filtres.add(p);
                }
            }
            inventaire.setAll(filtres);
        }
        tableInventaire.refresh();
    }

    private void validerInventaire() {
        List<StockService.InventoryLine> lignes = new ArrayList<>();
        for (Product p : inventaire) {
            int compte = comptes.getOrDefault(p.getId(), p.getQuantity());
            if (compte != p.getQuantity()) {
                lignes.add(new StockService.InventoryLine(p, compte));
            }
        }
        if (lignes.isEmpty()) {
            ToastService.get().info("Inventaire", "Aucun écart à valider.");
            return;
        }
        PromptDialog.text("Valider l'inventaire", "check", "Motif", "Inventaire physique",
                "Motif de l'ajustement", motif -> {
                    int ajustes = stock.applyInventory(lignes, motif);
                    ToastService.get().success("Inventaire",
                            String.valueOf(ajustes) + " écart(s) ajusté(s).");
                    toutRecharger();
                });
    }

    // --------------------------------------------------------------- appareils

    private VBox construireOngletAppareils() {
        filtreStatutAppareil = new ComboBox<>();
        filtreStatutAppareil.getStyleClass().add("field");
        filtreStatutAppareil.getItems().add("Tous les statuts");
        for (String s : STATUTS_APPAREIL) {
            filtreStatutAppareil.getItems().add(ProductUnit.libelle(s));
        }
        filtreStatutAppareil.getSelectionModel().selectFirst();
        filtreStatutAppareil.setOnAction(e -> chargerAppareils());

        rechercheAppareil = new TextField();
        rechercheAppareil.getStyleClass().add("field");
        rechercheAppareil.setPromptText("Rechercher un IMEI, un produit…");
        rechercheAppareil.setPrefWidth(300);
        rechercheAppareil.textProperty().addListener((o, a, b) -> chargerAppareils());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button rafraichir = Buttons.outline("Rafraîchir", "refresh-cw");
        rafraichir.setOnAction(e -> chargerAppareils());

        HBox barre = new HBox(10, filtreStatutAppareil, rechercheAppareil, spacer, rafraichir);
        barre.setAlignment(Pos.CENTER_LEFT);

        tableAppareils = new TableView<>(appareils);
        tableAppareils.getStyleClass().add("data-table");
        tableAppareils.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableAppareils, Priority.ALWAYS);
        construireColonnesAppareils();

        VBox carte = new VBox(tableAppareils);
        carte.getStyleClass().add("table-wrap");
        VBox.setVgrow(carte, Priority.ALWAYS);

        VBox contenu = new VBox(12, barre, carte);
        contenu.setPadding(new javafx.geometry.Insets(12, 0, 0, 0));
        return contenu;
    }

    private void construireColonnesAppareils() {
        TableColumn<ProductUnit, String> cProduit = colonne("Produit", ProductUnit::productName);
        cProduit.setPrefWidth(220);
        TableColumn<ProductUnit, String> cImei = colonne("IMEI", ProductUnit::imei);
        cImei.setPrefWidth(180);
        TableColumn<ProductUnit, String> cSerie = colonne("N° de série",
                u -> u.serialNumber() == null ? "—" : u.serialNumber());
        TableColumn<ProductUnit, String> cRecu = colonne("Reçu le",
                u -> Format.dateTime(u.receivedAt()));

        TableColumn<ProductUnit, ProductUnit> cStatut = new TableColumn<>("Statut");
        cStatut.setPrefWidth(170);
        cStatut.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStatut.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();
            private boolean maj;

            {
                combo.getStyleClass().add("field");
                combo.getItems().setAll(STATUTS_APPAREIL);
                combo.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(String s) {
                        return s == null ? "" : ProductUnit.libelle(s);
                    }

                    @Override
                    public String fromString(String s) {
                        return null;
                    }
                });
                combo.valueProperty().addListener((o, a, b) -> {
                    if (maj || b == null) {
                        return;
                    }
                    ProductUnit u = getItem();
                    if (u != null && !b.equals(u.status())) {
                        changerStatut(u, b);
                    }
                });
            }

            @Override
            protected void updateItem(ProductUnit u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                maj = true;
                combo.setValue(u.status());
                maj = false;
                setGraphic(combo);
            }
        });

        tableAppareils.getColumns().setAll(cProduit, cImei, cSerie, cStatut, cRecu);
    }

    private void changerStatut(ProductUnit u, String statut) {
        try {
            stock.setUnitStatus(u.id(), statut);
            chargerAppareils();
        } catch (ValidationException ex) {
            ToastService.get().error("Appareils", ex.getMessage());
        }
    }

    private void chargerAppareils() {
        String statut = statutAppareil();
        appareils.setAll(stock.units(new ProductUnitDao.Filter(null, statut, texteOuNull(rechercheAppareil))));
    }

    private String statutAppareil() {
        int i = filtreStatutAppareil == null ? 0 : filtreStatutAppareil.getSelectionModel().getSelectedIndex();
        return i <= 0 ? null : STATUTS_APPAREIL.get(i - 1);
    }

    // ---------------------------------------------------------------- helpers

    private void toutRecharger() {
        chargerMouvements();
        chargerInventaire();
        chargerAppareils();
        majCartes();
    }

    private void majCartes() {
        StockService.Summary s = stock.summary();
        carteValeur.setValue(Format.money(s.value()));
        carteReferences.setValue(Format.intStr(s.references()));
        carteFaible.setValue(Format.intStr(s.low()));
        carteRupture.setValue(Format.intStr(s.out()));
    }

    private Tab onglet(String titre, Region contenu) {
        Tab tab = new Tab(titre, contenu);
        tab.setClosable(false);
        return tab;
    }

    private boolean peut(PermissionService.Action action) {
        return permissions.can(PermissionDefaults.M_STOCK, action);
    }

    private String codeType() {
        if (filtreType == null) {
            return null;
        }
        int i = filtreType.getSelectionModel().getSelectedIndex();
        return i <= 0 ? null : CODES_TYPES[i];
    }

    private LocalDateTime du() {
        LocalDate d = filtreDu == null ? null : filtreDu.getValue();
        return d == null ? null : d.atStartOfDay();
    }

    private LocalDateTime au() {
        LocalDate d = filtreAu == null ? null : filtreAu.getValue();
        return d == null ? null : d.atTime(23, 59, 59);
    }

    private static String texteOuNull(TextField champ) {
        if (champ == null || champ.getText() == null || champ.getText().isBlank()) {
            return null;
        }
        return champ.getText().trim();
    }

    private static Tab ongletVide() {
        return null;
    }

    private static Tab onglet(String titre, Region contenu, boolean inutilise) {
        Tab t = new Tab(titre, contenu);
        t.setClosable(false);
        return t;
    }

    private static Badge.BadgeType badgeType(String type) {
        if (type == null) {
            return Badge.BadgeType.NEUTRAL;
        }
        return switch (type) {
            case "ENTREE", "RETOUR" -> Badge.BadgeType.SUCCESS;
            case "PERTE" -> Badge.BadgeType.DANGER;
            case "AJUSTEMENT", "INVENTAIRE" -> Badge.BadgeType.WARNING;
            case "VENTE", "REPARATION" -> Badge.BadgeType.INFO;
            default -> Badge.BadgeType.NEUTRAL;
        };
    }

    private static <T> TableColumn<T, String> colonne(String titre, java.util.function.Function<T, String> valeur) {
        TableColumn<T, String> col = new TableColumn<>(titre);
        col.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valeur.apply(d.getValue())));
        return col;
    }
}
