package com.techshop.controller;

import com.techshop.model.Brand;
import com.techshop.model.Category;
import com.techshop.model.Product;
import com.techshop.service.BarcodeService;
import com.techshop.service.CatalogService;
import com.techshop.service.ProductService;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import com.techshop.util.Images;
import com.techshop.util.ValidationException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Formulaire de produit (SPEC §4.5) : onglets Général, Prix, Stock et Options.
 * Construit en code, comme les formulaires du module Utilisateurs, pour réutiliser
 * les mêmes composants du design system. La marge se recalcule en direct.
 * Aucune règle métier ici : l'enregistrement passe par {@link ProductService}.
 */
public final class ProductFormDialog {

    /** Sentinelles « aucune » pour les listes déroulantes (identifiant 0). */
    private static final Category AUCUNE_CATEGORIE = new Category(0, "— Aucune —", true);
    private static final Brand AUCUNE_MARQUE = new Brand(0, "— Aucune —", true);

    private static final java.util.List<String> UNITES = java.util.List.of(
            "pièce", "boîte", "mètre", "lot", "kg", "heure");

    private final Product existant;
    private final Runnable onSaved;
    private final boolean creation;

    private final ProductService produits = ProductService.get();
    private final CatalogService catalogue = CatalogService.get();

    // --- champs ---
    private FormField fNom;
    private FormField fRef;
    private FormField fCodeBarres;
    private FormField fDescription;
    private FormField fAchat;
    private FormField fVente;
    private FormField fTva;
    private FormField fQuantite;
    private FormField fSeuil;
    private FormField fGarantie;
    private CheckBox cImei;
    private ComboBox<Category> cCategorie;
    private ComboBox<Brand> cMarque;
    private ComboBox<String> cUnite;
    private Label labelMarge;
    private ImageView apercuImage;
    private Label erreur;

    private java.nio.file.Path imageChoisie;
    private String imagePathActuel;

    private ProductFormDialog(Product existant, Runnable onSaved) {
        this.existant = existant;
        this.creation = existant == null;
        this.onSaved = onSaved;
    }

    /** Ouvre le formulaire (création si {@code existant} est null). */
    public static void ouvrir(Product existant, Runnable onSaved) {
        new ProductFormDialog(existant, onSaved).montrer();
    }

    // ------------------------------------------------------------------ montage

    private void montrer() {
        TabPane onglets = new TabPane();
        onglets.getStyleClass().add("tabs");
        onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        onglets.setPrefWidth(540);
        onglets.setPrefHeight(470);
        onglets.getTabs().setAll(
                onglet("Général", construireGeneral()),
                onglet("Prix", construirePrix()),
                onglet("Stock", construireStock()),
                onglet("Options", construireOptions()));

        erreur = new Label();
        erreur.getStyleClass().add("field-error-msg");
        erreur.setWrapText(true);
        erreur.setManaged(false);
        erreur.setVisible(false);

        VBox corps = new VBox(10, onglets, erreur);
        corps.setPrefWidth(540);

        Button annuler = Buttons.secondary("Annuler");
        Button enregistrer = Buttons.primary(creation ? "Créer" : "Enregistrer", "check");
        HBox pied = new HBox(8, annuler, enregistrer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card(
                creation ? "Nouveau produit" : "Modifier le produit",
                creation ? "plus-circle" : "edit-2", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        enregistrer.setOnAction(e -> enregistrer(handle));

        if (existant != null) {
            remplir(existant);
        } else {
            cUnite.setValue("pièce");
            cCategorie.getSelectionModel().selectFirst();
            cMarque.getSelectionModel().selectFirst();
            fTva.setValue("19");
            fSeuil.setValue("5");
            fGarantie.setValue("0");
            fQuantite.setValue("0");
        }
        majMarge();
        majApercu();
    }

    private Tab onglet(String titre, Region contenu) {
        ScrollPane defilement = new ScrollPane(contenu);
        defilement.setFitToWidth(true);
        defilement.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        defilement.getStyleClass().add("dialog-scroll");
        Tab tab = new Tab(titre, defilement);
        tab.setClosable(false);
        return tab;
    }

    private VBox construireGeneral() {
        fNom = Fields.text("Nom du produit", "ex. Samsung Galaxy A15 128 Go");
        fRef = Fields.text("Référence", "générée automatiquement");
        fRef.styledControl().setDisable(true);

        fCodeBarres = Fields.text("Code-barres (EAN-13 ou interne)",
                "généré automatiquement si vide");
        Button generer = Buttons.iconCompact("refresh-cw", "Générer un code-barres");
        generer.setOnAction(e -> {
            if (creation) {
                ToastService.get().info("Code-barres",
                        "Le code-barres sera généré automatiquement à l'enregistrement.");
                return;
            }
            fCodeBarres.setValue(BarcodeService.generateEan13(existant.getId()));
        });
        HBox ligneCode = new HBox(8, fCodeBarres, generer);
        ligneCode.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(fCodeBarres, javafx.scene.layout.Priority.ALWAYS);

        cCategorie = new ComboBox<>();
        cCategorie.getStyleClass().add("field");
        cCategorie.setMaxWidth(Double.MAX_VALUE);
        List<Category> categories = new ArrayList<>();
        categories.add(AUCUNE_CATEGORIE);
        categories.addAll(catalogue.categories(false));
        cCategorie.getItems().setAll(categories);
        FormField fCategorie = new FormField("Catégorie", cCategorie, cCategorie);

        cMarque = new ComboBox<>();
        cMarque.getStyleClass().add("field");
        cMarque.setMaxWidth(Double.MAX_VALUE);
        List<Brand> marques = new ArrayList<>();
        marques.add(AUCUNE_MARQUE);
        marques.addAll(catalogue.brands(false));
        cMarque.getItems().setAll(marques);
        FormField fMarque = new FormField("Marque", cMarque, cMarque);

        cUnite = new ComboBox<>();
        cUnite.getStyleClass().add("field");
        cUnite.setMaxWidth(Double.MAX_VALUE);
        cUnite.getItems().setAll(UNITES);
        FormField fUnite = new FormField("Unité", cUnite, cUnite);

        fDescription = Fields.area("Description", "Caractéristiques, couleur, capacité…");
        fDescription.styledControl().getStyleClass().add("text-area-description");
        if (fDescription.styledControl() instanceof TextArea zone) {
            zone.setPrefRowCount(6);
        }

        VBox contenu = new VBox(12, fNom, fRef, ligneCode, fCategorie, fMarque, fUnite,
                fDescription, construireBlocImage());
        contenu.setPadding(new Insets(4, 2, 4, 2));
        return contenu;
    }

    private VBox construirePrix() {
        fAchat = Fields.number("Prix d'achat", "ex. 15000");
        fVente = Fields.number("Prix de vente", "ex. 19900");
        fTva = Fields.number("TVA (%)", "19");

        labelMarge = new Label();
        labelMarge.getStyleClass().add("marge-value");
        VBox blocMarge = enteteCarte("Marge", "trending-up", labelMarge);

        ecouterMarge(fAchat);
        ecouterMarge(fVente);

        VBox contenu = new VBox(12, fAchat, fVente, fTva, blocMarge);
        contenu.setPadding(new Insets(4, 2, 4, 2));
        return contenu;
    }

    private VBox construireStock() {
        fQuantite = Fields.number("Quantité initiale", "0");
        if (!creation) {
            fQuantite.styledControl().setDisable(true);
            fQuantite.styledControl().setTooltip(
                    new Tooltip("La quantité se gère depuis le module Stock."));
        }
        fSeuil = Fields.number("Seuil d'alerte", "5");

        Label note = new Label(creation
                ? "La quantité initiale est enregistrée comme une entrée de stock."
                : "La quantité courante se modifie uniquement via un mouvement de stock.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        VBox contenu = new VBox(12, fQuantite, fSeuil, note);
        contenu.setPadding(new Insets(4, 2, 4, 2));
        return contenu;
    }

    private VBox construireOptions() {
        fGarantie = Fields.number("Garantie (mois)", "0");
        cImei = new CheckBox("Suivre les appareils par IMEI / numéro de série");

        Label note = new Label("Le suivi par IMEI ajoute une fiche par appareil physique, "
                + "consultable depuis l'onglet Appareils du module Stock.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        VBox contenu = new VBox(12, cImei, fGarantie, note);
        contenu.setPadding(new Insets(4, 2, 4, 2));
        return contenu;
    }

    // ------------------------------------------------------------------ image

    private VBox construireBlocImage() {
        apercuImage = new ImageView();
        apercuImage.setFitWidth(84);
        apercuImage.setFitHeight(84);
        apercuImage.setPreserveRatio(true);
        apercuImage.setSmooth(true);

        Label vide = new Label("—");
        vide.getStyleClass().add("muted");
        StackPane cadre = new StackPane(vide, apercuImage);
        cadre.getStyleClass().add("product-thumb-lg");
        cadre.setMinSize(84, 84);
        cadre.setPrefSize(84, 84);
        cadre.setMaxSize(84, 84);

        Button choisir = Buttons.outline("Choisir une image…", "image");
        choisir.setOnAction(e -> choisirImage());
        Button retirer = Buttons.ghost("Retirer", "trash-2");
        retirer.setOnAction(e -> {
            imageChoisie = null;
            imagePathActuel = null;
            majApercu();
        });

        Label titre = new Label("Image");
        titre.getStyleClass().add("field-label");
        VBox actions = new VBox(8, titre, new HBox(8, choisir, retirer));
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox ligne = new HBox(14, cadre, actions);
        ligne.setAlignment(Pos.CENTER_LEFT);
        return new VBox(ligne);
    }

    private void choisirImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image de produit");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File fichier = chooser.showOpenDialog(null);
        if (fichier != null) {
            imageChoisie = fichier.toPath();
            majApercu();
        }
    }

    private void majApercu() {
        if (apercuImage == null) {
            return;
        }
        Image image = null;
        if (imageChoisie != null) {
            image = new Image(imageChoisie.toUri().toString(), 84, 84, true, true, true);
        } else if (imagePathActuel != null && !imagePathActuel.isBlank()) {
            image = Images.thumbnail(imagePathActuel, 84);
        }
        apercuImage.setImage(image);
    }

    // ------------------------------------------------------------------- marge

    private void ecouterMarge(FormField champ) {
        if (champ.field() instanceof TextField tf) {
            tf.textProperty().addListener((o, a, b) -> majMarge());
        }
    }

    private void majMarge() {
        if (labelMarge == null) {
            return;
        }
        BigDecimal achat = lireMontant(fAchat, BigDecimal.ZERO);
        BigDecimal vente = lireMontant(fVente, BigDecimal.ZERO);
        BigDecimal marge = vente.subtract(achat);
        String taux = achat.signum() > 0
                ? "  (" + Format.percent(marge.multiply(BigDecimal.valueOf(100))
                .divide(achat, 1, RoundingMode.HALF_UP)) + ")"
                : "";
        labelMarge.setText(Format.money(marge) + taux);
        labelMarge.getStyleClass().removeAll("money-pos", "money-neg");
        labelMarge.getStyleClass().add(marge.signum() < 0 ? "money-neg" : "money-pos");
    }

    // ----------------------------------------------------------------- remplir

    private void remplir(Product p) {
        fNom.setValue(p.getName());
        fRef.setValue(p.getRef());
        fCodeBarres.setValue(p.getBarcode());
        selectionnerCategorie(p.getCategoryId());
        selectionnerMarque(p.getBrandId());
        cUnite.setValue(p.getUnit() == null || p.getUnit().isBlank() ? "pièce" : p.getUnit());
        fDescription.setValue(p.getDescription());
        fAchat.setValue(montantTexte(p.getPurchasePrice()));
        fVente.setValue(montantTexte(p.getSalePrice()));
        fTva.setValue(montantTexte(p.getVatRate()));
        fQuantite.setValue(String.valueOf(p.getQuantity()));
        fSeuil.setValue(String.valueOf(p.getAlertThreshold()));
        fGarantie.setValue(String.valueOf(p.getWarrantyMonths()));
        cImei.setSelected(p.isTrackImei());
        imagePathActuel = p.getImagePath();
    }

    private void selectionnerCategorie(Integer id) {
        if (id != null) {
            for (Category c : cCategorie.getItems()) {
                if (c.id() == id) {
                    cCategorie.setValue(c);
                    return;
                }
            }
        }
        cCategorie.getSelectionModel().selectFirst();
    }

    private void selectionnerMarque(Integer id) {
        if (id == null) {
            cMarque.getSelectionModel().selectFirst();
            return;
        }
        for (Brand b : cMarque.getItems()) {
            if (b.id() == id) {
                cMarque.setValue(b);
                return;
            }
        }
        cMarque.getSelectionModel().selectFirst();
    }

    // -------------------------------------------------------------- enregistrer

    private void enregistrer(DialogService.ModalHandle handle) {
        effacerErreurs();
        try {
            Product p = creation ? new Product() : copie(existant);
            p.setName(texte(fNom));
            p.setDescription(texteOuNull(fDescription));
            Category cat = cCategorie.getValue();
            p.setCategoryId(cat == null || cat.id() == 0 ? null : cat.id());
            Brand marque = cMarque.getValue();
            p.setBrandId(marque == null || marque.id() == 0 ? null : marque.id());
            p.setUnit(cUnite.getValue());
            p.setPurchasePrice(lireMontant(fAchat, BigDecimal.ZERO));
            p.setSalePrice(lireMontant(fVente, BigDecimal.ZERO));
            p.setVatRate(lireMontant(fTva, new BigDecimal("19")));
            p.setAlertThreshold(lireEntier(fSeuil, 5));
            p.setWarrantyMonths(lireEntier(fGarantie, 0));
            p.setTrackImei(cImei.isSelected());
            p.setBarcode(texteOuNull(fCodeBarres));
            if (!creation) {
                p.setImagePath(imagePathActuel);
            }

            if (creation) {
                p.setQuantity(Math.max(0, lireEntier(fQuantite, 0)));
                int id = produits.create(p);
                if (imageChoisie != null) {
                    produits.setImage(id, imageChoisie);
                }
                ToastService.get().success("Produit créé",
                        "« " + p.getName() + " » ajouté au catalogue (" + p.getRef() + ").");
            } else {
                produits.update(p);
                if (imageChoisie != null) {
                    produits.setImage(p.getId(), imageChoisie);
                }
                ToastService.get().success("Produit enregistré",
                        "« " + p.getName() + " » mis à jour.");
            }
            handle.close();
            if (onSaved != null) {
                onSaved.run();
            }
        } catch (ValidationException ex) {
            signaler(ex);
        } catch (RuntimeException ex) {
            ToastService.get().error("Produit", ex.getMessage());
        }
    }

    private void signaler(ValidationException ex) {
        FormField champ = switch (ex.getChamp() == null ? "" : ex.getChamp()) {
            case "name" -> fNom;
            case "salePrice" -> fVente;
            case "purchasePrice" -> fAchat;
            case "alertThreshold" -> fSeuil;
            case "warrantyMonths" -> fGarantie;
            case "barcode" -> fCodeBarres;
            default -> null;
        };
        if (champ != null) {
            champ.setError(ex.getMessage());
        }
        erreur.setText(ex.getMessage());
        erreur.setManaged(true);
        erreur.setVisible(true);
    }

    private void effacerErreurs() {
        erreur.setText(null);
        erreur.setManaged(false);
        erreur.setVisible(false);
        for (FormField f : List.of(fNom, fCodeBarres, fAchat, fVente, fSeuil, fGarantie)) {
            if (f != null) {
                f.clearError();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Product copie(Product src) {
        Product p = new Product();
        p.setId(src.getId());
        p.setRef(src.getRef());
        p.setBarcode(src.getBarcode());
        p.setName(src.getName());
        p.setDescription(src.getDescription());
        p.setCategoryId(src.getCategoryId());
        p.setBrandId(src.getBrandId());
        p.setUnit(src.getUnit());
        p.setPurchasePrice(src.getPurchasePrice());
        p.setAvgCost(src.getAvgCost());
        p.setSalePrice(src.getSalePrice());
        p.setVatRate(src.getVatRate());
        p.setAlertThreshold(src.getAlertThreshold());
        p.setQuantity(src.getQuantity());
        p.setTrackImei(src.isTrackImei());
        p.setWarrantyMonths(src.getWarrantyMonths());
        p.setImagePath(src.getImagePath());
        p.setActive(src.isActive());
        return p;
    }

    private static String texte(FormField f) {
        String v = f.textValue();
        return v == null ? null : v.trim();
    }

    private static String texteOuNull(FormField f) {
        String v = texte(f);
        return v == null || v.isBlank() ? null : v;
    }

    private BigDecimal lireMontant(FormField f, BigDecimal defaut) {
        String v = f == null ? null : f.textValue();
        if (v == null || v.isBlank()) {
            return defaut;
        }
        try {
            return new BigDecimal(v.replace(',', '.').replace(" ", "").trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(f == fVente ? "salePrice" : "purchasePrice",
                    "Le montant « " + v + " » n'est pas valide.");
        }
    }

    private static int lireEntier(FormField f, int defaut) {
        String v = f == null ? null : f.textValue();
        if (v == null || v.isBlank()) {
            return defaut;
        }
        try {
            return (int) Math.round(Double.parseDouble(v.replace(',', '.').trim()));
        } catch (NumberFormatException e) {
            throw new ValidationException("La valeur « " + v + " » n'est pas un nombre entier.");
        }
    }

    private static String montantTexte(BigDecimal v) {
        return v == null ? "0" : v.toPlainString().replace('.', ',');
    }

    /** Petite carte : titre (icône + libellé) puis valeur mise à jour. */
    private static VBox enteteCarte(String titre, String icone, Label valeur) {
        Label t = new Label(titre);
        t.getStyleClass().add("card-title");
        HBox entete = new HBox(8, Icons.feather(icone, 16), t);
        entete.setAlignment(Pos.CENTER_LEFT);
        VBox carte = new VBox(6, entete, valeur);
        carte.getStyleClass().add("card");
        return carte;
    }
}
