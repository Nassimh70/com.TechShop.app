package com.techshop.controller;

import com.techshop.dao.ProductDao;
import com.techshop.model.Brand;
import com.techshop.model.Category;
import com.techshop.model.Product;
import com.techshop.model.StockStatus;
import com.techshop.service.CatalogService;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.service.ProductService;
import com.techshop.service.StockService;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.StatCard;
import com.techshop.ui.components.TableFooter;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.Images;
import com.techshop.util.ValidationException;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Module Produits (SPEC §4.5, §6.1) : catalogue avec recherche, filtres,
 * pagination et tri, vue tableau ou grille (miniatures), actions par produit
 * (modifier, activer, étiquettes), import CSV et gestion des catégories/marques.
 * Aucune règle métier ici : tout passe par {@link ProductService}.
 */
public class ProductsController {

    private static final int TAILLE_PAGE = 20;

    private static final Category TOUTES_CATEGORIES = new Category(0, "Toutes les catégories", true);
    private static final Brand TOUTES_MARQUES = new Brand(0, "Toutes les marques", true);

    @FXML
    private VBox root;
    @FXML
    private HBox headerActions;

    private final ProductService produits = ProductService.get();
    private final StockService stock = StockService.get();
    private final CatalogService catalogue = CatalogService.get();
    private final PermissionService permissions = PermissionService.get();

    private final ObservableList<Product> donnees = FXCollections.observableArrayList();

    private TableView<Product> table;
    private TableColumn<Product, Product> colonneProduit;
    private FlowPane grille;
    private ScrollPane defilementGrille;
    private StackPane centre;
    private TableFooter pied;

    private TextField recherche;
    private ComboBox<Category> filtreCategorie;
    private ComboBox<Brand> filtreMarque;
    private ComboBox<String> filtreStatut;

    private StatCard carteTotal;
    private StatCard carteValeur;
    private StatCard carteFaible;
    private StatCard carteRupture;

    private int page = 1;
    private boolean vueGrille = false;
    private String tri = "name";
    private boolean ascendant = true;
    private boolean pret = false;

    @FXML
    private void initialize() {
        construireActions();

        HBox cartes = construireCartes();
        HBox barre = construireBarreFiltres();
        construireTable();
        construireGrille();

        centre = new StackPane();
        VBox.setVgrow(centre, Priority.ALWAYS);

        pied = new TableFooter();
        pied.setPrevAction(() -> {
            if (page > 1) {
                page--;
                charger();
            }
        });
        pied.setNextAction(() -> {
            page++;
            charger();
        });

        root.getChildren().setAll(cartes, barre, centre, pied);

        rechargerFiltres();
        charger();
        colonneProduit.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().setAll(colonneProduit);
        pret = true;
    }

    // ------------------------------------------------------------- barre d'actions

    private void construireActions() {
        if (peut(PermissionService.Action.EDIT)) {
            Button codes = Buttons.outline("Codes-barres", "hash");
            codes.setTooltip(new Tooltip("Générer les codes-barres manquants"));
            codes.setOnAction(e -> genererCodes());
            headerActions.getChildren().add(codes);
        }
        if (peut(PermissionService.Action.CREATE)) {
            Button importer = Buttons.outline("Importer CSV", "upload");
            importer.setOnAction(e -> CsvImportDialog.ouvrir(() -> {
                page = 1;
                charger();
            }));
            headerActions.getChildren().add(importer);
        }
        if (peut(PermissionService.Action.EDIT)) {
            Button catalogueBtn = Buttons.outline("Catégories & marques", "tag");
            catalogueBtn.setOnAction(e -> CatalogDialog.ouvrir(this::rechargerFiltres));
            headerActions.getChildren().add(catalogueBtn);
        }

        Button etiquettes = Buttons.outline("Étiquettes", "printer");
        etiquettes.setOnAction(e -> etiqueter());
        headerActions.getChildren().add(etiquettes);

        if (peut(PermissionService.Action.CREATE)) {
            Button nouveau = Buttons.primary("Nouveau produit", "plus");
            nouveau.setOnAction(e -> ProductFormDialog.ouvrir(null, () -> {
                page = 1;
                charger();
            }));
            headerActions.getChildren().add(nouveau);
        }
    }

    private HBox construireCartes() {
        carteTotal = new StatCard("Références actives", "0");
        carteValeur = new StatCard("Valeur du stock", "0");
        carteFaible = new StatCard("Stock faible", "0");
        carteRupture = new StatCard("Rupture", "0");
        List<StatCard> cartes = List.of(carteTotal, carteValeur, carteFaible, carteRupture);
        for (StatCard c : cartes) {
            c.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(c, Priority.ALWAYS);
        }
        return new HBox(12, carteTotal, carteValeur, carteFaible, carteRupture);
    }

    private HBox construireBarreFiltres() {
        FormField champ = Fields.search("Rechercher un nom, une référence, un code-barres…");
        recherche = (TextField) champ.field();
        recherche.setPrefWidth(300);
        PauseTransition pause = new PauseTransition(Duration.millis(250));
        pause.setOnFinished(e -> {
            page = 1;
            charger();
        });
        recherche.textProperty().addListener((o, a, b) -> pause.playFromStart());

        filtreCategorie = new ComboBox<>();
        filtreCategorie.getStyleClass().add("field");
        filtreCategorie.setPrefWidth(190);
        filtreCategorie.setOnAction(e -> {
            page = 1;
            charger();
        });

        filtreMarque = new ComboBox<>();
        filtreMarque.getStyleClass().add("field");
        filtreMarque.setPrefWidth(170);
        filtreMarque.setOnAction(e -> {
            page = 1;
            charger();
        });

        filtreStatut = new ComboBox<>(FXCollections.observableArrayList(
                "Tous les stocks", "En stock", "Stock faible", "Rupture"));
        filtreStatut.getStyleClass().add("field");
        filtreStatut.getSelectionModel().selectFirst();
        filtreStatut.setOnAction(e -> {
            page = 1;
            charger();
        });

        Button basculer = Buttons.outline("Grille", "grid");
        basculer.setOnAction(e -> {
            vueGrille = !vueGrille;
            basculer.setText(vueGrille ? "Tableau" : "Grille");
            basculer.setGraphic(Icons.feather(vueGrille ? "list" : "grid", 16));
            majCentre();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox barre = new HBox(10, recherche, filtreCategorie, filtreMarque, filtreStatut,
                spacer, basculer);
        barre.setAlignment(Pos.CENTER_LEFT);
        return barre;
    }

    // -------------------------------------------------------------------- tableau

    private void construireTable() {
        table = new TableView<>(donnees);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        colonneProduit = new TableColumn<>("Produit");
        colonneProduit.setUserData("name");
        colonneProduit.setMinWidth(260);
        colonneProduit.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        colonneProduit.setComparator(Comparator.comparing(Product::getName,
                Comparator.nullsLast(String::compareToIgnoreCase)));
        colonneProduit.setCellFactory(col -> new TableCell<>() {
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
                Label ref = new Label(p.getRef() + (p.getBarcode() == null ? "" : "  ·  " + p.getBarcode()));
                ref.getStyleClass().add("muted");
                VBox textes = new VBox(0, nom, ref);
                HBox boite = new HBox(10, vignette(p), textes);
                boite.setAlignment(Pos.CENTER_LEFT);
                setGraphic(boite);
            }
        });

        TableColumn<Product, String> cCategorie = new TableColumn<>("Catégorie");
        cCategorie.setUserData("category");
        cCategorie.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getCategoryName() == null ? "—" : d.getValue().getCategoryName()));
        cCategorie.setComparator(Comparator.nullsLast(String::compareToIgnoreCase));

        TableColumn<Product, String> cMarque = new TableColumn<>("Marque");
        cMarque.setUserData("brand");
        cMarque.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getBrandName() == null ? "—" : d.getValue().getBrandName()));
        cMarque.setComparator(Comparator.nullsLast(String::compareToIgnoreCase));

        TableColumn<Product, Product> cPrix = new TableColumn<>("Prix de vente");
        cPrix.setUserData("salePrice");
        cPrix.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cPrix.setComparator(Comparator.comparing(Product::getSalePrice,
                Comparator.nullsLast(Comparator.naturalOrder())));
        cPrix.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                Label montant = new Label(Format.money(p.getSalePrice()));
                montant.getStyleClass().add("cell-money");
                setGraphic(montant);
            }
        });

        TableColumn<Product, Product> cStock = new TableColumn<>("Stock");
        cStock.setUserData("quantity");
        cStock.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStock.setComparator(Comparator.comparingInt(Product::getQuantity));
        cStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                Label quantite = new Label(Format.intStr(p.getQuantity()));
                quantite.getStyleClass().add("cell-text");
                Badge badge = Badge.of(p.stockStatus().getLabel(), badgeDe(p.stockStatus()));
                HBox boite = new HBox(8, quantite, badge);
                boite.setAlignment(Pos.CENTER_LEFT);
                setGraphic(boite);
            }
        });

        TableColumn<Product, Product> cActions = new TableColumn<>("");
        cActions.setMinWidth(130);
        cActions.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                HBox actions = new HBox(6);
                if (peut(PermissionService.Action.EDIT)) {
                    Button modifier = Buttons.iconCompact("edit-2", "Modifier");
                    modifier.setOnAction(e -> ProductFormDialog.ouvrir(p, () -> charger()));
                    actions.getChildren().add(modifier);

                    Button basculer = Buttons.iconCompact(p.isActive() ? "eye-off" : "eye",
                            p.isActive() ? "Désactiver" : "Activer");
                    basculer.setOnAction(e -> basculerActivation(p));
                    actions.getChildren().add(basculer);
                }
                Button etiquette = Buttons.iconCompact("printer", "Étiquettes");
                etiquette.setOnAction(e -> LabelDialog.ouvrir(List.of(p)));
                actions.getChildren().add(etiquette);
                setGraphic(actions);
            }
        });

        table.getColumns().setAll(colonneProduit, cCategorie, cMarque, cPrix, cStock, cActions);

        table.getSortOrder().addListener((ListChangeListener<TableColumn<Product, ?>>) change -> {
            if (!pret || table.getSortOrder().isEmpty()) {
                return;
            }
            TableColumn<Product, ?> col = table.getSortOrder().get(0);
            if (col.getUserData() != null) {
                tri = col.getUserData().toString();
                ascendant = col.getSortType() == TableColumn.SortType.ASCENDING;
                page = 1;
                charger();
            }
        });
    }

    // -------------------------------------------------------------------- grille

    private void construireGrille() {
        grille = new FlowPane(12, 12);
        grille.getStyleClass().add("product-grid");
        defilementGrille = new ScrollPane(grille);
        defilementGrille.setFitToWidth(true);
        defilementGrille.getStyleClass().add("product-grid-scroll");
    }

    // -------------------------------------------------------------------- centre

    private void majCentre() {
        if (centre == null) {
            return;
        }
        if (donnees.isEmpty()) {
            EmptyState vide = EmptyState.of("box", "Aucun produit",
                    "Aucun produit ne correspond à votre recherche. Ajustez les filtres "
                            + "ou ajoutez un nouveau produit.");
            if (peut(PermissionService.Action.CREATE)) {
                vide.withAction("Nouveau produit", () -> ProductFormDialog.ouvrir(null, () -> {
                    page = 1;
                    charger();
                }));
            }
            centre.getChildren().setAll(vide);
            return;
        }
        if (vueGrille) {
            centre.getChildren().setAll(defilementGrille);
        } else {
            VBox carte = new VBox(table);
            carte.getStyleClass().add("table-wrap");
            VBox.setVgrow(carte, Priority.ALWAYS);
            centre.getChildren().setAll(carte);
        }
    }

    // ---------------------------------------------------------------- chargement

    private void charger() {
        try {
            ProductDao.Filter filtre = new ProductDao.Filter(
                    texteOuNull(recherche), idCategorie(), idMarque(), statutFiltre(), Boolean.TRUE);
            ProductService.Page resultat = produits.search(filtre, page, TAILLE_PAGE, tri, ascendant);
            if (page > resultat.pageCount()) {
                page = resultat.pageCount();
                resultat = produits.search(filtre, page, TAILLE_PAGE, tri, ascendant);
            }
            donnees.setAll(resultat.items());
            pied.setResultCount((int) resultat.total());
            pied.setPage(resultat.page(), resultat.pageCount());
            construireTuiles();
            majCentre();
            majCartes();
        } catch (ValidationException ex) {
            ToastService.get().error("Produits", ex.getMessage());
        }
    }

    private void majCartes() {
        StockService.Summary s = stock.summary();
        carteTotal.setValue(Format.intStr(s.references()));
        carteValeur.setValue(Format.money(s.value()));
        carteFaible.setValue(Format.intStr(s.low()));
        carteRupture.setValue(Format.intStr(s.out()));
    }

    private void construireTuiles() {
        grille.getChildren().clear();
        for (Product p : donnees) {
            grille.getChildren().add(tuile(p));
        }
    }

    private Region tuile(Product p) {
        ImageView apercu = new ImageView();
        apercu.setFitWidth(96);
        apercu.setFitHeight(96);
        apercu.setPreserveRatio(true);
        apercu.setSmooth(true);
        Image image = Images.thumbnail(p.getImagePath(), 96);
        if (image != null) {
            apercu.setImage(image);
        }
        Label placeholder = new Label("—");
        placeholder.getStyleClass().add("muted");
        StackPane cadre = new StackPane(placeholder, apercu);
        cadre.getStyleClass().add("product-tile-image");
        cadre.setMinHeight(104);

        Label nom = new Label(p.getName());
        nom.getStyleClass().add("product-tile-name");
        nom.setWrapText(true);
        Label ref = new Label(p.getRef());
        ref.getStyleClass().add("muted");
        Button prix = new Button(Format.money(p.getSalePrice()));
        prix.getStyleClass().addAll("btn", "btn-ghost", "product-tile-price");
        prix.setMouseTransparent(true);
        Badge badge = Badge.of(p.stockStatus().getLabel(), badgeDe(p.stockStatus()));
        HBox piedTuile = new HBox(8, prix, badge);
        piedTuile.setAlignment(Pos.CENTER_LEFT);

        VBox tuile = new VBox(6, cadre, nom, ref, piedTuile);
        tuile.getStyleClass().add("product-tile");
        tuile.setPrefWidth(184);
        tuile.setOnMouseClicked(e -> ProductFormDialog.ouvrir(p, () -> charger()));
        return tuile;
    }

    // ----------------------------------------------------------- actions produit

    private void etiqueter() {
        List<Product> cible = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (cible.isEmpty()) {
            cible = new ArrayList<>(donnees);
        }
        if (cible.isEmpty()) {
            ToastService.get().warning("Étiquettes", "Aucun produit à étiqueter.");
            return;
        }
        LabelDialog.ouvrir(cible);
    }

    private void genererCodes() {
        try {
            int manquants = produits.generateMissingBarcodes();
            if (manquants == 0) {
                ToastService.get().info("Codes-barres",
                        "Tous les produits possèdent déjà un code-barres.");
            } else {
                ToastService.get().success("Codes-barres", manquants + " code(s)-barres générés.");
                charger();
            }
        } catch (ValidationException ex) {
            ToastService.get().error("Codes-barres", ex.getMessage());
        }
    }

    private void basculerActivation(Product p) {
        try {
            p.setActive(!p.isActive());
            produits.update(p);
            ToastService.get().success("Produit",
                    "« " + p.getName() + " » " + (p.isActive() ? "activé" : "désactivé") + ".");
            charger();
        } catch (ValidationException ex) {
            ToastService.get().error("Produit", ex.getMessage());
        }
    }

    // -------------------------------------------------------------------- filtres

    private void rechargerFiltres() {
        Integer categorie = idCategorie();
        Integer marque = idMarque();

        List<Category> cats = new ArrayList<>();
        cats.add(TOUTES_CATEGORIES);
        cats.addAll(catalogue.categories(true));
        filtreCategorie.getItems().setAll(cats);
        filtreCategorie.setValue(chercherCategorie(cats, categorie));

        List<Brand> brs = new ArrayList<>();
        brs.add(TOUTES_MARQUES);
        brs.addAll(catalogue.brands(true));
        filtreMarque.getItems().setAll(brs);
        filtreMarque.setValue(chercherMarque(brs, marque));

        if (pret) {
            page = 1;
            charger();
        }
    }

    private static Category chercherCategorie(List<Category> items, Integer id) {
        if (id != null) {
            for (Category c : items) {
                if (c.id() == id) {
                    return c;
                }
            }
        }
        return TOUTES_CATEGORIES;
    }

    private static Brand chercherMarque(List<Brand> items, Integer id) {
        if (id != null) {
            for (Brand b : items) {
                if (b.id() == id) {
                    return b;
                }
            }
        }
        return TOUTES_MARQUES;
    }

    private Integer idCategorie() {
        Category c = filtreCategorie == null ? null : filtreCategorie.getValue();
        return c == null || c.id() == 0 ? null : c.id();
    }

    private Integer idMarque() {
        Brand b = filtreMarque == null ? null : filtreMarque.getValue();
        return b == null || b.id() == 0 ? null : b.id();
    }

    private StockStatus statutFiltre() {
        if (filtreStatut == null) {
            return null;
        }
        return switch (filtreStatut.getSelectionModel().getSelectedIndex()) {
            case 1 -> StockStatus.OK;
            case 2 -> StockStatus.LOW;
            case 3 -> StockStatus.OUT;
            default -> null;
        };
    }

    private static String texteOuNull(TextField champ) {
        if (champ == null || champ.getText() == null || champ.getText().isBlank()) {
            return null;
        }
        return champ.getText().trim();
    }

    // -------------------------------------------------------------------- helpers

    private boolean peut(PermissionService.Action action) {
        return permissions.can(PermissionDefaults.M_PRODUCTS, action);
    }

    private static Badge.BadgeType badgeDe(StockStatus statut) {
        return switch (statut) {
            case OK -> Badge.BadgeType.SUCCESS;
            case LOW -> Badge.BadgeType.WARNING;
            case OUT -> Badge.BadgeType.DANGER;
        };
    }

    private static StackPane vignette(Product p) {
        ImageView iv = new ImageView();
        iv.setFitWidth(32);
        iv.setFitHeight(32);
        iv.setPreserveRatio(true);
        Image image = Images.thumbnail(p.getImagePath(), 32);
        if (image != null) {
            iv.setImage(image);
        }
        StackPane cadre = new StackPane(iv);
        cadre.getStyleClass().add("product-thumb");
        cadre.setMinSize(40, 40);
        cadre.setPrefSize(40, 40);
        cadre.setMaxSize(40, 40);
        return cadre;
    }
}
