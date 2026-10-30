package com.techshop.controller;

import com.techshop.service.ProductService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.ToastService;
import com.techshop.util.ValidationException;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Import CSV du catalogue (SPEC §4.5) : sélection d'un fichier, import via
 * {@link ProductService} puis rapport détaillé (créés, mis à jour, erreurs).
 * Un modèle de fichier est proposé pour guider la saisie.
 */
public final class CsvImportDialog {

    private static final String ENTETE = "ref;nom;categorie;marque;code_barres;prix_achat;"
            + "prix_vente;tva;seuil;quantite;imei;garantie;unite";

    private final Runnable onImported;
    private final ProductService produits = ProductService.get();

    private TextField chemin;
    private VBox resultat;

    private CsvImportDialog(Runnable onImported) {
        this.onImported = onImported;
    }

    public static void ouvrir(Runnable onImported) {
        new CsvImportDialog(onImported).montrer();
    }

    private void montrer() {
        Label intro = new Label("Importez un fichier CSV (séparateur « ; », encodage UTF-8). "
                + "Les produits dont la référence existe déjà sont mis à jour ; les autres sont créés.");
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);
        intro.setMaxWidth(460);

        Label colonnes = new Label("Colonnes attendues : " + ENTETE);
        colonnes.getStyleClass().add("muted");
        colonnes.setWrapText(true);
        colonnes.setMaxWidth(460);

        chemin = new TextField();
        chemin.getStyleClass().add("field");
        chemin.setEditable(false);
        chemin.setPromptText("Aucun fichier sélectionné");
        Button choisir = Buttons.outline("Choisir un fichier…", "folder");
        choisir.setOnAction(e -> choisirFichier());
        HBox ligne = new HBox(8, chemin, choisir);
        HBox.setHgrow(chemin, Priority.ALWAYS);
        ligne.setAlignment(Pos.CENTER_LEFT);

        Button modele = Buttons.ghost("Créer un modèle de fichier", "download");
        modele.setOnAction(e -> creerModele());

        resultat = new VBox(6);

        VBox corps = new VBox(12, intro, colonnes, ligne, modele, resultat);
        corps.setPrefWidth(480);

        Button fermer = Buttons.secondary("Fermer");
        HBox pied = new HBox(fermer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Importer des produits (CSV)", "upload", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);
        fermer.setOnAction(e -> handle.close());
    }

    private void choisirFichier() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un fichier CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv", "*.txt"));
        File fichier = chooser.showOpenDialog(null);
        if (fichier == null) {
            return;
        }
        chemin.setText(fichier.getAbsolutePath());
        importer(fichier.toPath());
    }

    private void importer(Path fichier) {
        resultat.getChildren().clear();
        try {
            ProductService.CsvReport rapport = produits.importCsv(fichier);
            resultat.getChildren().add(ligneInfo("Produits traités", String.valueOf(rapport.total()), true));
            resultat.getChildren().add(ligneInfo("Créés", String.valueOf(rapport.created()), true));
            resultat.getChildren().add(ligneInfo("Mis à jour", String.valueOf(rapport.updated()), true));
            resultat.getChildren().add(ligneInfo("Erreurs", String.valueOf(rapport.errors().size()),
                    rapport.sansErreur()));
            if (!rapport.errors().isEmpty()) {
                int max = Math.min(8, rapport.errors().size());
                for (int i = 0; i < max; i++) {
                    Label l = new Label("• " + rapport.errors().get(i));
                    l.getStyleClass().add("field-error-msg");
                    l.setWrapText(true);
                    l.setMaxWidth(440);
                    resultat.getChildren().add(l);
                }
                if (rapport.errors().size() > max) {
                    Label plus = new Label("… et " + (rapport.errors().size() - max) + " autre(s).");
                    plus.getStyleClass().add("muted");
                    resultat.getChildren().add(plus);
                }
            }
            ToastService.get().success("Import CSV",
                    rapport.created() + " créé(s), " + rapport.updated() + " mis à jour.");
            onImported.run();
        } catch (ValidationException ex) {
            ToastService.get().error("Import CSV", ex.getMessage());
            Label l = new Label(ex.getMessage());
            l.getStyleClass().add("field-error-msg");
            l.setWrapText(true);
            resultat.getChildren().add(l);
        }
    }

    private static HBox ligneInfo(String label, String valeur, boolean ok) {
        Label l = new Label(label);
        l.getStyleClass().add("muted");
        javafx.scene.layout.Region esp = new javafx.scene.layout.Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        Label v = new Label(valeur);
        v.getStyleClass().add(ok ? "money-pos" : "field-error-msg");
        HBox box = new HBox(8, l, esp, v);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void creerModele() {
        try {
            Path dossier = Files.createDirectories(
                    Path.of(System.getProperty("user.dir"), "data", "import"));
            Path fichier = dossier.resolve("modele-produits.csv");
            String contenu = ENTETE + System.lineSeparator()
                    + ";Chargeur USB-C 25W;Accessoires;Générique;2000000000194;1200;1900;19;5;20;non;6;pièce"
                    + System.lineSeparator();
            Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
            ToastService.get().success("Modèle CSV",
                    "Modèle créé : " + fichier.toAbsolutePath());
        } catch (IOException ex) {
            ToastService.get().error("Modèle CSV", "Création impossible : " + ex.getMessage());
        }
    }
}
