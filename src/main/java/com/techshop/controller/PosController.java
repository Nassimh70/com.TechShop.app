package com.techshop.controller;

import com.techshop.model.Category;
import com.techshop.model.Product;
import com.techshop.service.CashService;
import com.techshop.service.CatalogService;
import com.techshop.service.SaleDocumentService;
import com.techshop.service.SaleService;
import com.techshop.service.SettingService;
import com.techshop.ui.components.Buttons;
import com.techshop.util.Format;
import com.techshop.util.Numbers;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Écran Point de vente (SPEC §4.3) : recherche / scan code-barres (F2), puces de
 * catégories, grille ou liste de produits, plusieurs paniers en attente, panier
 * avec quantités (+/-) et remises (par ligne et globale, F8), client (F4),
 * modes de paiement et encaissement (F10) avec rendu de monnaie, saisie IMEI
 * obligatoire pour les produits suivis. L'impression du ticket est déléguée au
 * {@link SaleDocumentService}.
 */
public class PosController {

    @FXML private TextField searchField;
    @FXML private FlowPane categoryChips;
    @FXML private ScrollPane gridScroll;
    @FXML private FlowPane productGrid;
    @FXML private HBox cartTabs;
    @FXML private Label clientLabel;
    @FXML private Button btnClient;
    @FXML private VBox cartTableBox;
    @FXML private TextField discountField;
    @FXML private Label lblSubtotal;
    @FXML private Label lblDiscount;
    @FXML private Label lblTotal;
    @FXML private Button btnPayer;
    @FXML private Button btnAttente;
    @FXML private Button btnVider;
    @FXML private HBox headerActions;

    private final com.techshop.dao.ProductDao productsDao = new com.techshop.dao.ProductDao();
    private final CatalogService catalog = CatalogService.get();
    private final SaleService ventes = SaleService.get();
    private final CashService caisse = CashService.get();

    private final ObservableList<Product> tousProduits = FXCollections.observableArrayList();
    private final ObservableList<Product> filtreProduits = FXCollections.observableArrayList();
    private final Set<Integer> categoriesSelection = new HashSet<>();

    private com.techshop.model.Customer client;
    private final List<Panier> paniers = new ArrayList<>();
    private int actif = 0;
    private int compteur = 0;
    private boolean majAuto = false;
    private boolean modeListe = false;

    private TableView<LignePanier> tablePanier;
    private TableView<Product> tableProduits;

    /** Ligne du panier : produit, quantité, IMEI éventuel, prix et remise. */
    private record LignePanier(Product produit, int quantite, Integer unitId, String imei,
                               BigDecimal prixUnitaire, BigDecimal remiseLigne, int garantieMois) {
        BigDecimal total() {
            BigDecimal brut = prixUnitaire.multiply(BigDecimal.valueOf(quantite));
            BigDecimal net = brut.subtract(remiseLigne);
            return net.signum() < 0 ? BigDecimal.ZERO : net;
        }
    }

    /** Panier en attente, indépendant des autres (numéroté). */
    private static final class Panier {
        final int numero;
        final ObservableList<LignePanier> lignes = FXCollections.observableArrayList();
        BigDecimal remiseGlobale = BigDecimal.ZERO;

        Panier(int numero) {
            this.numero = numero;
        }
    }

    @FXML
    private void initialize() {
        headerActions.getChildren().clear();
        Button nouveau = Buttons.outline("Nouveau panier", "plus");
        nouveau.setOnAction(e -> nouveauPanier());
        ToggleButton vue = new ToggleButton("Vue liste");
        vue.getStyleClass().add("chip");
        vue.setOnAction(e -> {
            modeListe = vue.isSelected();
            vue.setText(modeListe ? "Vue grille" : "Vue liste");
            appliquerModeVue();
        });
        headerActions.getChildren().addAll(nouveau, vue);

        lblSubtotal.setText("0,00 DA");
        lblDiscount.setText("0,00 DA");
        lblTotal.setText("0,00 DA");
        clientLabel.setText("Client : Comptoir");
        client = null;

        btnClient.setOnAction(e -> choisirClient());
        // La largeur du champ doit suivre celle du panneau gauche, sans être
        // réduite par la largeur minimale du tableau lorsque le catalogue est
        // volumineux.
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.setMinWidth(0);
        categoryChips.setMaxWidth(Double.MAX_VALUE);
        categoryChips.setMinWidth(0);
        gridScroll.setMaxWidth(Double.MAX_VALUE);
        gridScroll.setMinWidth(0);
        productGrid.setMaxWidth(Double.MAX_VALUE);
        productGrid.setMinWidth(0);
        discountField.textProperty().addListener((o, a, v) -> {
            if (majAuto) {
                return;
            }
            panierActif().remiseGlobale = parseRemise(v);
            rafraichirPanier();
        });
        btnAfficherActions();
        searchField.setOnAction(e -> rechercherBarcode());
        searchField.textProperty().addListener((o, a, v) -> filtrer());

        construireTablePanier();
        construireTableProduits();
        chargerCategories();
        chargerProduits();
        paniers.add(new Panier(++compteur));
        activerPanier(0);
    }

    private void btnAfficherActions() {
        btnVider.setOnAction(e -> {
            panierActif().lignes.clear();
            panierActif().remiseGlobale = BigDecimal.ZERO;
            majChampRemise();
            rafraichirPanier();
        });
        btnAttente.setOnAction(e -> nouveauPanier());
        btnPayer.setOnAction(e -> encaisser());
    }

    // ------------------------------------------------------------- paniers

    private Panier panierActif() {
        return paniers.get(actif);
    }

    /** Ouvre un nouveau panier (met le panier courant en attente). */
    private void nouveauPanier() {
        if (!paniers.isEmpty() && panierActif().lignes.isEmpty()) {
            return; // le panier courant est déjà vide
        }
        paniers.add(new Panier(++compteur));
        activerPanier(paniers.size() - 1);
        searchField.requestFocus();
    }

    private void activerPanier(int index) {
        if (index < 0 || index >= paniers.size()) {
            return;
        }
        actif = index;
        if (tablePanier != null) {
            tablePanier.setItems(panierActif().lignes);
        }
        majChampRemise();
        construireOngletsPanier();
        rafraichirPanier();
    }

    private void fermerPanier(int index) {
        if (paniers.size() <= 1 && panierActif().lignes.isEmpty()) {
            return;
        }
        paniers.remove(index);
        if (paniers.isEmpty()) {
            paniers.add(new Panier(++compteur));
        }
        actif = Math.min(actif, paniers.size() - 1);
        activerPanier(actif);
    }

    private void construireOngletsPanier() {
        cartTabs.getChildren().clear();
        for (int i = 0; i < paniers.size(); i++) {
            Panier p = paniers.get(i);
            final int idx = i;
            Button chip = new Button("Panier " + p.numero
                    + (p.lignes.isEmpty() ? "" : " (" + p.lignes.size() + ")"));
            chip.getStyleClass().addAll("cart-tab",
                    i == actif ? "cart-tab-active" : "cart-tab-inactive");
            chip.setOnAction(e -> activerPanier(idx));
            cartTabs.getChildren().add(chip);
            if (paniers.size() > 1) {
                Button fermer = Buttons.iconCompact("x", "Fermer ce panier");
                fermer.setOnAction(e -> fermerPanier(idx));
                cartTabs.getChildren().add(fermer);
            }
        }
    }

    private void majChampRemise() {
        majAuto = true;
        discountField.setText(panierActif().remiseGlobale.signum() == 0
                ? "" : Format.moneyShort(panierActif().remiseGlobale));
        majAuto = false;
    }

    private static BigDecimal parseRemise(String v) {
        if (v == null || v.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal d = Numbers.money(v);
            return d.signum() < 0 ? BigDecimal.ZERO : d;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ------------------------------------------------------------- produits

    private void chargerProduits() {
        try {
            tousProduits.setAll(productsDao.allActive());
        } catch (SQLException e) {
            tousProduits.clear();
        }
        filtrer();
    }

    private void chargerCategories() {
        categoryChips.getChildren().clear();
        ToggleGroup g = new ToggleGroup();
        ToggleButton tout = new ToggleButton("Tout");
        tout.setToggleGroup(g);
        tout.setSelected(true);
        tout.getStyleClass().add("chip");
        tout.setOnAction(e -> {
            categoriesSelection.clear();
            filtrer();
        });
        categoryChips.getChildren().add(tout);
        try {
            catalog.categories(false).stream()
                    .sorted(Comparator.comparing(Category::name))
                    .forEach(c -> {
                        ToggleButton tb = new ToggleButton(c.name());
                        tb.setToggleGroup(g);
                        tb.getStyleClass().add("chip");
                        tb.setOnAction(e -> {
                            categoriesSelection.clear();
                            categoriesSelection.add(c.id());
                            filtrer();
                        });
                        categoryChips.getChildren().add(tb);
                    });
        } catch (Exception ignored) {
            // catégories indisponibles : on garde la puce « Tout ».
        }
    }

    private void filtrer() {
        String v = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtreProduits.setAll(tousProduits.stream()
                .filter(p -> v.isEmpty() || contient(p.getName(), v)
                        || contient(p.getBarcode(), v) || contient(p.getRef(), v))
                .filter(p -> categoriesSelection.isEmpty()
                        || (p.getCategoryId() != null && categoriesSelection.contains(p.getCategoryId())))
                .sorted(Comparator.comparing(Product::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
        afficherGrille();
    }

    private static boolean contient(String valeur, String recherche) {
        return valeur != null && valeur.toLowerCase().contains(recherche);
    }

    /** Scan code-barres ou recherche par référence, validé par Entrée (F2). */
    private void rechercherBarcode() {
        String v = searchField.getText() == null ? "" : searchField.getText().trim();
        if (v.isEmpty()) {
            return;
        }
        try {
            Product trouve = productsDao.findByBarcode(v).orElse(null);
            if (trouve == null) {
                trouve = productsDao.findByRef(v).orElse(null);
            }
            if (trouve != null) {
                ajouterProduit(trouve);
            } else {
                com.techshop.ui.components.ToastService.get().warning("Produit introuvable",
                        "Aucun produit pour « " + v + " ».");
            }
        } catch (Exception e) {
            com.techshop.ui.components.ToastService.get().error("Recherche", e.getMessage());
        }
        searchField.clear();
        searchField.requestFocus();
    }

    private void afficherGrille() {
        productGrid.getChildren().clear();
        for (Product p : filtreProduits) {
            productGrid.getChildren().add(carteProduit(p));
        }
    }

    private VBox carteProduit(Product p) {
        VBox tile = new VBox(6);
        tile.getStyleClass().add("product-tile");
        Label nom = new Label(p.getName());
        nom.getStyleClass().add("product-tile-name");
        nom.setWrapText(true);
        Label prix = new Label(Format.money(p.getSalePrice()));
        prix.getStyleClass().add("product-tile-price");
        HBox badges = new HBox(4);
        if (p.isTrackImei()) {
            badges.getChildren().add(com.techshop.ui.components.Badge.of("IMEI",
                    com.techshop.ui.components.Badge.BadgeType.INFO));
        }
        if (p.getQuantity() <= 0) {
            badges.getChildren().add(com.techshop.ui.components.Badge.of("Rupture",
                    com.techshop.ui.components.Badge.BadgeType.DANGER));
        } else if (p.getQuantity() <= p.getAlertThreshold()) {
            badges.getChildren().add(com.techshop.ui.components.Badge.of("Faible",
                    com.techshop.ui.components.Badge.BadgeType.WARNING));
        } else {
            badges.getChildren().add(com.techshop.ui.components.Badge.of("En stock",
                    com.techshop.ui.components.Badge.BadgeType.SUCCESS));
        }
        Label stock = new Label(Format.count(p.getQuantity()) + " " + p.getUnit());
        stock.getStyleClass().add("muted");
        VBox.setVgrow(nom, Priority.ALWAYS);
        tile.getChildren().addAll(nom, badges, new VBox(2, prix, stock));
        tile.setPrefSize(150, 120);
        tile.setOnMouseClicked(e -> ajouterProduit(p));
        return tile;
    }

    private void construireTableProduits() {
        tableProduits = new TableView<>(filtreProduits);
        tableProduits.getStyleClass().add("data-table");
        tableProduits.setPlaceholder(com.techshop.ui.components.EmptyState.of(
                "package", "Aucun produit", "Modifiez la recherche ou la catégorie."));
        tableProduits.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> cNom = new TableColumn<>("Produit");
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getName()));
        TableColumn<Product, String> cPrix = new TableColumn<>("Prix");
        cPrix.setMinWidth(110);
        cPrix.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.money(d.getValue().getSalePrice())));
        TableColumn<Product, String> cStock = new TableColumn<>("Stock");
        cStock.setMinWidth(110);
        cStock.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                Format.count(d.getValue().getQuantity()) + " " + d.getValue().getUnit()));
        tableProduits.getColumns().setAll(List.of(cNom, cPrix, cStock));
        tableProduits.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) {
                    ajouterProduit(row.getItem());
                }
            });
            return row;
        });
    }

    /** Bascule grille / liste (SPEC §4.3). */
    private void appliquerModeVue() {
        gridScroll.setContent(modeListe ? tableProduits : productGrid);
    }

    // ------------------------------------------------------------- panier

    private void construireTablePanier() {
        tablePanier = new TableView<>();
        tablePanier.getStyleClass().add("data-table");
        tablePanier.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablePanier.setPlaceholder(com.techshop.ui.components.EmptyState.of("shopping-cart",
                "Panier vide", "Sélectionnez un produit pour commencer la vente."));
        VBox.setVgrow(tablePanier, Priority.ALWAYS);

        TableColumn<LignePanier, String> cProduit = new TableColumn<>("Produit");
        cProduit.setMinWidth(140);
        cProduit.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().produit().getName()
                + (d.getValue().imei() == null ? "" : "\nIMEI " + d.getValue().imei())));

        TableColumn<LignePanier, LignePanier> cQte = new TableColumn<>("Qté");
        cQte.setMinWidth(120);
        cQte.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cQte.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(LignePanier l, boolean empty) {
                super.updateItem(l, empty);
                setText(null);
                setGraphic(null);
                if (empty || l == null) {
                    return;
                }
                boolean fixe = l.unitId() != null; // produit suivi par IMEI : quantité figée
                Button moins = Buttons.iconCompact("minus", "Diminuer");
                Button plus = Buttons.iconCompact("plus", "Augmenter");
                moins.setDisable(fixe || l.quantite() <= 1);
                plus.setDisable(fixe);
                moins.setOnAction(e -> changerQuantite(l, l.quantite() - 1));
                plus.setOnAction(e -> changerQuantite(l, l.quantite() + 1));
                Label q = new Label(String.valueOf(l.quantite()));
                q.getStyleClass().add("cell-text");
                q.setMinWidth(20);
                q.setAlignment(Pos.CENTER);
                HBox box = new HBox(4, moins, q, plus);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        TableColumn<LignePanier, String> cPu = new TableColumn<>("P.U.");
        cPu.setMinWidth(90);
        cPu.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().prixUnitaire())));

        TableColumn<LignePanier, LignePanier> cRemise = new TableColumn<>("Remise");
        cRemise.setMinWidth(90);
        cRemise.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cRemise.setCellFactory(c -> new TableCell<>() {
            private final TextField champ = new TextField();
            {
                champ.getStyleClass().add("field");
                champ.setPrefWidth(80);
                champ.setOnAction(e -> valider());
                champ.focusedProperty().addListener((o, a, focus) -> {
                    if (!focus) {
                        valider();
                    }
                });
            }

            private void valider() {
                LignePanier l = getItem();
                if (l == null) {
                    return;
                }
                BigDecimal r = parseRemise(champ.getText());
                if (r.compareTo(l.prixUnitaire().multiply(BigDecimal.valueOf(l.quantite()))) > 0) {
                    r = l.prixUnitaire().multiply(BigDecimal.valueOf(l.quantite()));
                }
                remplacerLigne(l, new LignePanier(l.produit(), l.quantite(), l.unitId(),
                        l.imei(), l.prixUnitaire(), r, l.garantieMois()));
            }

            @Override protected void updateItem(LignePanier l, boolean empty) {
                super.updateItem(l, empty);
                setText(null);
                if (empty || l == null) {
                    setGraphic(null);
                    return;
                }
                if (!champ.isFocused()) {
                    champ.setText(l.remiseLigne().signum() == 0
                            ? "" : Format.moneyShort(l.remiseLigne()));
                }
                setGraphic(champ);
            }
        });

        TableColumn<LignePanier, String> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(100);
        cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().total())));

        TableColumn<LignePanier, LignePanier> cAct = new TableColumn<>("");
        cAct.setMinWidth(56);
        cAct.setMaxWidth(56);
        cAct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cAct.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(LignePanier l, boolean empty) {
                super.updateItem(l, empty);
                setText(null);
                setGraphic(null);
                if (empty || l == null) {
                    return;
                }
                Button sup = Buttons.iconCompact("trash-2", "Supprimer la ligne");
                sup.getStyleClass().add("btn-danger");
                sup.setOnAction(e -> {
                    panierActif().lignes.remove(l);
                    rafraichirPanier();
                });
                setGraphic(sup);
            }
        });

        tablePanier.getColumns().setAll(List.of(cProduit, cQte, cPu, cRemise, cTotal, cAct));
        cartTableBox.getChildren().setAll(tablePanier);
    }

    private void changerQuantite(LignePanier l, int q) {
        if (q <= 0) {
            return;
        }
        remplacerLigne(l, new LignePanier(l.produit(), q, l.unitId(), l.imei(),
                l.prixUnitaire(), l.remiseLigne(), l.garantieMois()));
    }

    private void remplacerLigne(LignePanier ancien, LignePanier nouveau) {
        int i = panierActif().lignes.indexOf(ancien);
        if (i >= 0) {
            panierActif().lignes.set(i, nouveau);
        }
        rafraichirPanier();
    }

    /** Ajoute un produit au panier (saisie IMEI si le produit est suivi). */
    private void ajouterProduit(Product p) {
        if (p.getQuantity() <= 0) {
            com.techshop.ui.components.ToastService.get().warning("Rupture de stock",
                    p.getName() + " n'est plus disponible.");
            return;
        }
        if (p.isTrackImei()) {
            ImeiDialog.ouvrir(p, (ch, ok) -> {
                if (ok && ch != null) {
                    ajouterLigne(p, 1, ch.unitId(), ch.imei(), p.getSalePrice(),
                            BigDecimal.ZERO, ch.warrantyMonths());
                }
            });
            return;
        }
        ajouterLigne(p, 1, null, null, p.getSalePrice(), BigDecimal.ZERO, p.getWarrantyMonths());
    }

    private void ajouterLigne(Product p, int q, Integer unitId, String imei, BigDecimal pu,
                              BigDecimal remise, int garantie) {
        panierActif().lignes.add(new LignePanier(p, Math.max(q, 1), unitId, imei,
                pu == null ? BigDecimal.ZERO : pu, remise == null ? BigDecimal.ZERO : remise, garantie));
        construireOngletsPanier();
        rafraichirPanier();
    }

    private void choisirClient() {
        CustomerPickerDialog.ouvrir(sel -> {
            client = sel;
            clientLabel.setText("Client : " + (sel == null ? "Comptoir" : sel.getFullName()));
        });
    }

    private void rafraichirPanier() {
        BigDecimal sousTotal = BigDecimal.ZERO;
        BigDecimal remise = BigDecimal.ZERO;
        for (LignePanier l : panierActif().lignes) {
            sousTotal = sousTotal.add(l.prixUnitaire().multiply(BigDecimal.valueOf(l.quantite())));
            remise = remise.add(l.remiseLigne());
        }
        remise = remise.add(panierActif().remiseGlobale);
        BigDecimal total = sousTotal.subtract(remise);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }
        lblSubtotal.setText(Format.money(sousTotal));
        lblDiscount.setText(Format.money(remise));
        lblTotal.setText(Format.money(total));
    }

    private void encaisser() {
        try {
            caisse.requireOpen();
        } catch (ValidationException e) {
            com.techshop.ui.components.ToastService.get().warning("Caisse fermée", e.getMessage());
            return;
        }
        if (panierActif().lignes.isEmpty()) {
            com.techshop.ui.components.ToastService.get().warning("Panier vide",
                    "Ajoutez au moins un produit.");
            return;
        }
        BigDecimal sousTotal = BigDecimal.ZERO;
        BigDecimal remise = panierActif().remiseGlobale;
        for (LignePanier l : panierActif().lignes) {
            sousTotal = sousTotal.add(l.prixUnitaire().multiply(BigDecimal.valueOf(l.quantite())));
            remise = remise.add(l.remiseLigne());
        }
        BigDecimal total = sousTotal.subtract(remise);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }

        final Panier enCours = panierActif();
        PaymentDialog.ouvrir(total, client, result -> {
            var venteClient = result.customer();
            List<SaleService.PaymentLine> paiements = result.payments();
            List<SaleService.CheckoutLine> lignes = enCours.lignes.stream().map(l ->
                    new SaleService.CheckoutLine(l.produit(), l.quantite(), l.unitId(), l.imei(),
                            l.prixUnitaire(), l.remiseLigne(), l.garantieMois())).toList();
            Integer cid = venteClient == null ? null : venteClient.getId();
            SaleService.CheckoutRequest req = new SaleService.CheckoutRequest(cid, lignes,
                    enCours.remiseGlobale, paiements, null);
            try {
                SaleService.CheckoutResult res = ventes.checkout(req);
                com.techshop.ui.components.ToastService.get().success("Vente enregistrée",
                        res.sale().getNumber() + " — " + Format.money(res.sale().getTotal()));
                try {
                    var pdf = SaleDocumentService.ticket(res.sale());
                    if (SettingService.get().autoPrintTicket()) {
                        SaleDocumentService.imprimer(pdf);
                    } else {
                        com.techshop.ui.components.ToastService.get().info("Ticket enregistré",
                                "Impression automatique désactivée — PDF : " + pdf.getFileName());
                    }
                } catch (Exception ex) {
                    com.techshop.ui.components.ToastService.get().warning("Impression",
                            "Ticket enregistré mais impression impossible : " + ex.getMessage());
                }
                fermerPanier(paniers.indexOf(enCours));
                client = null;
                clientLabel.setText("Client : Comptoir");
                chargerProduits();
            } catch (ValidationException ex) {
                com.techshop.ui.components.ToastService.get().error("Encaissement impossible",
                        ex.getMessage());
            }
        });
    }

    @FXML
    private void onKeyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case F2 -> { searchField.requestFocus(); e.consume(); }
            case F4 -> { btnClient.fire(); e.consume(); }
            case F8 -> {
                discountField.requestFocus();
                discountField.selectAll();
                e.consume();
            }
            case F10 -> { btnPayer.fire(); e.consume(); }
            case ESCAPE -> {
                panierActif().lignes.clear();
                panierActif().remiseGlobale = BigDecimal.ZERO;
                majChampRemise();
                rafraichirPanier();
                e.consume();
            }
            default -> { }
        }
    }
}
