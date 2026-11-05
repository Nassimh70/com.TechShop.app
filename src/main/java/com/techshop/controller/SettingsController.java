package com.techshop.controller;

import com.techshop.config.AppConfig;
import com.techshop.config.ThemeManager;
import com.techshop.config.Tx;
import com.techshop.dao.SettingDao;
import com.techshop.license.LicenseService;
import com.techshop.service.BackupService;
import com.techshop.ui.Icons;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.ui.components.ToggleSwitch;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Page Paramètres (SPEC §4.13) : identité du magasin, documents, apparence et
 * sauvegarde de la base. Les valeurs sont lues / écrites dans la table
 * {@code settings} et utilisées directement par les services de documents.
 */
public class SettingsController {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private VBox nav;
    @FXML
    private VBox contenu;
    @FXML
    private ScrollPane scroll;
    @FXML
    private Button btnSave;
    @FXML
    private Label lblEtat;

    private final SettingDao dao = new SettingDao();
    private final Map<String, Node> sections = new LinkedHashMap<>();
    private final Map<String, Button> navItems = new LinkedHashMap<>();
    private String sectionActive = "magasin";
    private boolean chargement = true;

    // --- Magasin ---
    private FormField fShopName;
    private FormField fShopPhone;
    private FormField fShopEmail;
    private FormField fShopAddress;
    private FormField fShopTaxId;

    // --- Documents ---
    private FormField fFooter;
    private ToggleSwitch tAutoPrint;
    private ToggleSwitch tShowVat;
    private ToggleSwitch tLogo;
    private ImageView logoPreview;
    private Label lblLogo;
    private String logoPath;

    // --- Apparence ---
    private ToggleSwitch tDark;

    // --- Sauvegarde ---
    private FormField fBackupDir;
    private ToggleSwitch tAutoBackup;
    private Label lblBackup;
    private Button btnBackup;
    private Button btnRestore;

    // --- Licence ---
    private Label licenseBadge;
    private Label licenseMessage;
    private Label licenseCustomer;
    private Label licenseType;
    private Label licenseExpiry;
    private Label licensePath;

    @FXML
    void initialize() {
        btnSave.setGraphic(Icons.feather("save", 16));
        nav.setMaxHeight(Double.MAX_VALUE);
        construireSections();
        charger();
        ecouterModifications();
        afficherSection(sectionActive);
        chargement = false;
    }

    // ------------------------------------------------------------- construction

    private void construireSections() {
        sections.put("magasin", contenuMagasin());
        sections.put("documents", contenuDocuments());
        sections.put("apparence", contenuApparence());
        sections.put("sauvegarde", contenuSauvegarde());
        sections.put("licence", contenuLicence());
        sections.put("apropos", contenuAPropos());

        nav.getChildren().clear();
        navItems.clear();
        ajouterNav("magasin", "Magasin", "home");
        ajouterNav("documents", "Documents", "file-text");
        ajouterNav("apparence", "Apparence", "moon");
        ajouterNav("sauvegarde", "Sauvegarde", "database");
        ajouterNav("licence", "Licence", "key");
        ajouterNav("apropos", "À propos", "info");
    }

    private void ajouterNav(String id, String titre, String icone) {
        Button bouton = new Button(titre);
        bouton.getStyleClass().add("settings-nav-item");
        bouton.setGraphic(Icons.feather(icone, 17));
        bouton.setMaxWidth(Double.MAX_VALUE);
        bouton.setAlignment(Pos.CENTER_LEFT);
        bouton.setGraphicTextGap(10);
        bouton.setOnAction(e -> afficherSection(id));
        navItems.put(id, bouton);
        nav.getChildren().add(bouton);
    }

    private void afficherSection(String id) {
        Node section = sections.get(id);
        if (section == null) {
            return;
        }
        sectionActive = id;
        contenu.getChildren().setAll(section);
        navItems.forEach((cle, bouton) -> {
            bouton.getStyleClass().remove("active");
            if (cle.equals(id)) {
                bouton.getStyleClass().add("active");
            }
        });
        if (scroll != null) {
            scroll.setVvalue(0);
        }
    }

    private Node contenuMagasin() {
        fShopName = Fields.text("Nom du magasin", "Ex. TechShop Manager");
        fShopPhone = Fields.text("Téléphone", "Ex. 0550 00 00 00");
        fShopEmail = Fields.text("Email", "Ex. contact@techshop.dz");
        fShopAddress = Fields.text("Adresse", "Ex. 12 rue Didouche Mourad, Alger");
        fShopTaxId = Fields.text("NIF / RC (facultatif)", "Ex. 123456789");

        GridPane grille = new GridPane();
        grille.setHgap(16);
        grille.setVgap(14);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        col2.setPercentWidth(50);
        grille.getColumnConstraints().addAll(col1, col2);

        grille.add(fShopName, 0, 0);
        grille.add(fShopPhone, 1, 0);
        grille.add(fShopEmail, 0, 1);
        grille.add(fShopTaxId, 1, 1);
        grille.add(fShopAddress, 0, 2, 2, 1);

        return new VBox(16, carte("Identité du magasin",
                "Ces informations apparaissent en en-tête des tickets et des factures.", grille));
    }

    private Node contenuDocuments() {
        fFooter = Fields.text("Pied de ticket / facture", "Ex. Merci de votre confiance.");
        Label apercu = new Label();
        apercu.getStyleClass().addAll("muted", "settings-hint");
        apercu.setWrapText(true);
        TextField champPied = (TextField) fFooter.styledControl();
        champPied.textProperty().addListener((o, ancien, nouveau) ->
                apercu.setText("Aperçu : « " + (nouveau == null || nouveau.isBlank() ? "—" : nouveau) + " »"));

        tAutoPrint = new ToggleSwitch();
        tShowVat = new ToggleSwitch();
        tLogo = new ToggleSwitch();

        VBox carteDocuments = carte("Documents",
                "Personnalisez le ticket de caisse et les factures.",
                fFooter,
                apercu,
                ligneToggle("Impression automatique",
                        "Imprimer le ticket après chaque vente en caisse.", tAutoPrint),
                ligneToggle("Afficher la TVA",
                        "Afficher la ligne TVA sur le ticket et la facture.", tShowVat),
                ligneToggle("Logo sur les documents",
                        "Ajouter le logo du magasin aux tickets et factures.", tLogo),
                ligneLogo());

        return new VBox(16, carteDocuments);
    }

    private HBox ligneLogo() {
        logoPreview = new ImageView();
        logoPreview.setFitWidth(56);
        logoPreview.setFitHeight(56);
        logoPreview.setPreserveRatio(true);
        logoPreview.setSmooth(true);
        StackPane cadre = new StackPane(logoPreview);
        cadre.getStyleClass().addAll("settings-logo", "card-inset");

        lblLogo = new Label("Aucun logo sélectionné");
        lblLogo.getStyleClass().add("muted");

        Label titre = new Label("Logo du magasin");
        titre.getStyleClass().add("settings-row-title");

        Button choisir = Buttons.secondary("Choisir", "image");
        choisir.setOnAction(e -> choisirLogo());
        Button retirer = Buttons.ghost("Retirer", "trash-2");
        retirer.setOnAction(e -> {
            logoPath = null;
            majLogo();
            marquerModifie();
        });

        HBox actions = new HBox(8, choisir, retirer);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox infos = new VBox(4, titre, lblLogo, actions);

        HBox ligne = new HBox(14, cadre, infos);
        ligne.getStyleClass().add("settings-row");
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    private Node contenuApparence() {
        tDark = new ToggleSwitch();
        VBox carte = carte("Apparence",
                "Le thème s'applique à toute l'application après enregistrement.",
                ligneToggle("Thème sombre",
                        "Fond sombre et couleurs adaptées pour travailler le soir.", tDark));
        return new VBox(16, carte);
    }

    private Node contenuSauvegarde() {
        fBackupDir = Fields.text("Dossier des sauvegardes", "Ex. %APPDATA%\\TechShopManager\\backups");

        Button parcourirDossier = Buttons.secondary("Parcourir");
        parcourirDossier.setOnAction(e -> choisirDossier(fBackupDir, "Dossier des sauvegardes"));

        HBox ligneDossier = ligneChampBouton(fBackupDir, parcourirDossier);

        tAutoBackup = new ToggleSwitch();

        btnBackup = Buttons.primary("Sauvegarder maintenant", "download");
        btnBackup.setOnAction(e -> lancerSauvegarde());
        btnRestore = Buttons.secondary("Restaurer une sauvegarde", "upload");
        btnRestore.setOnAction(e -> lancerRestauration());

        lblBackup = new Label("Aucune sauvegarde effectuée dans cette session.");
        lblBackup.getStyleClass().add("muted");
        lblBackup.setWrapText(true);

        VBox carte = carte("Sauvegarde de la base de données",
                "La sauvegarde copie le fichier SQLite de façon cohérente. La restauration remplace toutes les données actuelles.",
                ligneDossier,
                ligneToggle("Sauvegarde automatique",
                        "Créer une sauvegarde à la fermeture de l'application.", tAutoBackup),
                new HBox(8, btnBackup, btnRestore),
                lblBackup);
        return new VBox(16, carte);
    }

    private Node contenuLicence() {
        licenseBadge = new Label();
        licenseBadge.getStyleClass().add("badge");
        licenseMessage = new Label();
        licenseMessage.getStyleClass().add("muted");
        licenseMessage.setWrapText(true);

        licenseCustomer = licenceValue();
        licenseType = licenceValue();
        licenseExpiry = licenceValue();

        GridPane infos = new GridPane();
        infos.setHgap(16);
        infos.setVgap(8);
        infos.add(licenceLabel("Client"), 0, 0);
        infos.add(licenseCustomer, 1, 0);
        infos.add(licenceLabel("Type"), 0, 1);
        infos.add(licenseType, 1, 1);
        infos.add(licenceLabel("Expiration"), 0, 2);
        infos.add(licenseExpiry, 1, 2);

        TextField device = new TextField(LicenseService.deviceId());
        device.setEditable(false);
        device.setFocusTraversable(false);
        device.getStyleClass().add("mono");
        device.setStyle("-fx-font-size: 11px;");
        HBox.setHgrow(device, Priority.ALWAYS);
        Button copy = Buttons.secondary("Copier", "copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(device.getText());
            Clipboard.getSystemClipboard().setContent(content);
            ToastService.get().success("Licence", "Identifiant de l'ordinateur copié.");
        });
        HBox deviceRow = new HBox(8, device, copy);
        deviceRow.setAlignment(Pos.CENTER_LEFT);

        Button importer = Buttons.primary("Importer une licence...", "upload");
        importer.setOnAction(e -> importerLicence());
        Button dossier = Buttons.secondary("Ouvrir le dossier", "folder");
        dossier.setOnAction(e -> ouvrirDossierLicence());
        HBox actions = new HBox(8, importer, dossier);

        licensePath = new Label();
        licensePath.getStyleClass().add("muted");
        licensePath.setWrapText(true);

        VBox statut = new VBox(6, licenseBadge, licenseMessage);
        statut.getStyleClass().add("card-inset");

        refreshLicenseUi();

        return new VBox(16, carte("Licence du logiciel",
                "La licence est vérifiée localement à chaque démarrage. "
                        + "Communiquez l'identifiant ci-dessous pour obtenir un fichier .lic.",
                statut, infos, deviceRow, actions, licensePath));
    }

    private void refreshLicenseUi() {
        if (licenseBadge == null) {
            return;
        }
        LicenseService.LicenseStatus status = LicenseService.current();
        licenseBadge.setText(status.activityLabel());
        licenseBadge.getStyleClass().removeAll("badge-success", "badge-warning",
                "badge-danger", "badge-info", "badge-neutral");
        licenseBadge.getStyleClass().add(status.badgeStyle());
        licenseMessage.setText(status.message());

        licenseCustomer.setText(status.customerName() == null ? "—" : status.customerName());
        licenseType.setText(status.licenseTypeLabel() == null ? "—" : status.licenseTypeLabel());
        if (status.expiresAt() == null) {
            licenseExpiry.setText(status.isPresent() ? "Sans expiration" : "—");
        } else {
            long jours = status.daysRemaining();
            licenseExpiry.setText(status.expiresAtLabel()
                    + (jours >= 0 ? " (dans " + jours + " jour" + (jours > 1 ? "s" : "") + ")" : ""));
        }
        licensePath.setText("Fichier : " + status.file());
    }

    private void importerLicence() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sélectionner le fichier de licence");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Licence TechShop (*.lic)", "*.lic"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
        File selection = chooser.showOpenDialog(fenetre());
        if (selection == null) {
            return;
        }
        try {
            LicenseService.LicenseStatus installed = LicenseService.install(selection.toPath());
            refreshLicenseUi();
            ToastService.get().success("Licence",
                    "Licence installée pour " + installed.customerName() + ".");
        } catch (LicenseService.LicenseInstallException e) {
            ToastService.get().error("Licence refusée", e.getMessage());
        } catch (RuntimeException e) {
            ToastService.get().error("Licence", "Installation impossible : " + e.getMessage());
        }
    }

    private void ouvrirDossierLicence() {
        try {
            Path directory = LicenseService.licenseDirectory();
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
            } else {
                ToastService.get().info("Licence", directory.toString());
            }
        } catch (Exception e) {
            ToastService.get().error("Licence", "Dossier inaccessible : " + e.getMessage());
        }
    }

    private static Label licenceLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private static Label licenceValue() {
        Label label = new Label("—");
        label.setWrapText(true);
        return label;
    }

    private Node contenuAPropos() {
        Label application = new Label("TechShop Manager 1.0.0");
        application.getStyleClass().add("card-title");
        Label stack = new Label("JavaFX 21 · SQLite · HikariCP · OpenPDF");
        stack.getStyleClass().add("muted");
        Label base = new Label("Base de données : " + AppConfig.databasePath());
        base.getStyleClass().add("muted");
        base.setWrapText(true);
        Label droits = new Label("© 2026 TechShop Manager");
        droits.getStyleClass().add("muted");
        return new VBox(16, carte("À propos", null, new VBox(6, application, stack, base, droits)));
    }

    // ---------------------------------------------------------------- chargement

    private void charger() {
        try {
            Map<String, String> valeurs = dao.all();
            fShopName.setValue(premier(valeurs, "TechShop", "store.name", "shop.name"));
            fShopPhone.setValue(premier(valeurs, "", "store.phone", "shop.phone"));
            fShopEmail.setValue(premier(valeurs, "", "store.email", "shop.email"));
            fShopAddress.setValue(premier(valeurs, "", "store.address", "shop.address"));
            fShopTaxId.setValue(premier(valeurs, "", "store.tax_id", "shop.tax_id"));
            fFooter.setValue(premier(valeurs, "Merci de votre confiance.", "ticket.footer"));

            tAutoPrint.setSelected(bool(valeurs, "print.auto_ticket", true));
            tShowVat.setSelected(bool(valeurs, "sales.vat_on_ticket", true));

            logoPath = premier(valeurs, "", "print.logo_path", "shop.logo");
            tLogo.setSelected(bool(valeurs, "print.logo", logoPath != null && !logoPath.isBlank()));
            majLogo();

            tDark.setSelected(bool(valeurs, "theme.dark", ThemeManager.isDark()));
            fBackupDir.setValue(premier(valeurs, dossierParDefaut(), "backup.dir"));
            tAutoBackup.setSelected(bool(valeurs, "backup.auto_on_exit", false));

            lblEtat.setText("Paramètres chargés");
            lblEtat.getStyleClass().remove("warning-text");
        } catch (Exception e) {
            ToastService.get().error("Paramètres", "Chargement impossible : " + e.getMessage());
            lblEtat.setText("Chargement impossible");
        }
    }

    private void ecouterModifications() {
        for (FormField champ : List.of(fShopName, fShopPhone, fShopEmail, fShopAddress,
                fShopTaxId, fFooter, fBackupDir)) {
            if (champ.styledControl() instanceof TextField tf) {
                tf.textProperty().addListener((o, ancien, nouveau) -> marquerModifie());
            }
        }
        for (ToggleSwitch toggle : List.of(tAutoPrint, tShowVat, tLogo, tDark, tAutoBackup)) {
            toggle.selectedProperty().addListener((o, ancien, nouveau) -> marquerModifie());
        }
    }

    private void marquerModifie() {
        if (chargement) {
            return;
        }
        lblEtat.setText("Modifications non enregistrées");
        if (!lblEtat.getStyleClass().contains("warning-text")) {
            lblEtat.getStyleClass().add("warning-text");
        }
    }

    // --------------------------------------------------------------- enregistrer

    @FXML
    void onSave() {
        clearErrors();
        if (!valider()) {
            return;
        }
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("shop.name", fShopName.textValue().trim());
        valeurs.put("shop.address", fShopAddress.textValue().trim());
        valeurs.put("shop.phone", fShopPhone.textValue().trim());
        valeurs.put("shop.email", fShopEmail.textValue().trim());
        valeurs.put("shop.tax_id", fShopTaxId.textValue().trim());
        valeurs.put("ticket.footer", fFooter.textValue().trim());
        valeurs.put("print.auto_ticket", String.valueOf(tAutoPrint.isSelected()));
        valeurs.put("sales.vat_on_ticket", String.valueOf(tShowVat.isSelected()));
        valeurs.put("print.logo", String.valueOf(tLogo.isSelected()));
        valeurs.put("shop.logo", logoPath == null ? "" : logoPath);
        valeurs.put("theme.dark", String.valueOf(tDark.isSelected()));
        valeurs.put("backup.dir", fBackupDir.textValue().trim());
        valeurs.put("backup.auto_on_exit", String.valueOf(tAutoBackup.isSelected()));
        // Anciennes clés de l'écran Paramètres : vidées après migration vers shop.*
        for (String ancienne : List.of("store.name", "store.address", "store.phone",
                "store.email", "store.tax_id", "print.logo_path")) {
            valeurs.put(ancienne, "");
        }

        try {
            Tx.run(() -> {
                for (Map.Entry<String, String> entree : valeurs.entrySet()) {
                    dao.set(entree.getKey(), entree.getValue());
                }
                return null;
            });
            ThemeManager.setTheme(tDark.isSelected()
                    ? ThemeManager.Theme.DARK : ThemeManager.Theme.LIGHT);
            lblEtat.setText("Enregistré à " + LocalDateTime.now().format(HEURE));
            lblEtat.getStyleClass().remove("warning-text");
            ToastService.get().success("Paramètres", "Enregistrés");
            if (tLogo.isSelected() && (logoPath == null || logoPath.isBlank())) {
                ToastService.get().warning("Logo",
                        "Aucun logo sélectionné : l'option sera ignorée sur les documents.");
            }
        } catch (Exception e) {
            ToastService.get().error("Paramètres",
                    "Enregistrement impossible : " + e.getMessage());
        }
    }

    private boolean valider() {
        if (fShopName.textValue() == null || fShopName.textValue().isBlank()) {
            afficherSection("magasin");
            fShopName.setError("Le nom du magasin est obligatoire.");
            fShopName.styledControl().requestFocus();
            return false;
        }
        String email = fShopEmail.textValue();
        if (email != null && !email.isBlank()
                && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            afficherSection("magasin");
            fShopEmail.setError("Adresse email invalide.");
            fShopEmail.styledControl().requestFocus();
            return false;
        }
        if (fBackupDir.textValue() == null || fBackupDir.textValue().isBlank()) {
            afficherSection("sauvegarde");
            fBackupDir.setError("Indiquez un dossier de sauvegarde.");
            fBackupDir.styledControl().requestFocus();
            return false;
        }
        return true;
    }

    private void clearErrors() {
        for (FormField champ : List.of(fShopName, fShopPhone, fShopEmail, fShopAddress,
                fShopTaxId, fFooter, fBackupDir)) {
            champ.clearError();
        }
    }

    // ------------------------------------------------------------------- logo

    private void choisirLogo() {
        FileChooser choix = new FileChooser();
        choix.setTitle("Choisir le logo du magasin");
        choix.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg"));
        File fichier = choix.showOpenDialog(fenetre());
        if (fichier == null) {
            return;
        }
        try {
            Path dossier = Path.of(System.getProperty("user.dir"), "data", "logo");
            Files.createDirectories(dossier);
            Path cible = dossier.resolve(fichier.getName());
            Files.copy(fichier.toPath(), cible, StandardCopyOption.REPLACE_EXISTING);
            logoPath = cible.toString();
            tLogo.setSelected(true);
            majLogo();
            marquerModifie();
        } catch (Exception e) {
            ToastService.get().error("Logo", e.getMessage());
        }
    }

    private void majLogo() {
        if (logoPath == null || logoPath.isBlank()) {
            logoPreview.setImage(null);
            lblLogo.setText("Aucun logo sélectionné");
            return;
        }
        try {
            Path fichier = Path.of(logoPath);
            if (!Files.isRegularFile(fichier)) {
                logoPreview.setImage(null);
                lblLogo.setText("Fichier introuvable : " + logoPath);
                return;
            }
            logoPreview.setImage(new Image(fichier.toUri().toString(), 56, 56, true, true));
            lblLogo.setText(fichier.getFileName().toString());
        } catch (Exception e) {
            logoPreview.setImage(null);
            lblLogo.setText("Logo illisible");
        }
    }

    // --------------------------------------------------------------- sauvegarde

    private void choisirDossier(FormField champ, String titre) {
        DirectoryChooser choix = new DirectoryChooser();
        choix.setTitle(titre);
        String actuel = champ.textValue();
        if (actuel != null && !actuel.isBlank()) {
            try {
                Path dossier = Path.of(actuel.trim());
                if (Files.isDirectory(dossier)) {
                    choix.setInitialDirectory(dossier.toFile());
                }
            } catch (Exception ignored) {
                // chemin invalide : le sélecteur reste sur son dossier par défaut
            }
        }
        File fichier = choix.showDialog(fenetre());
        if (fichier != null) {
            champ.setValue(fichier.getAbsolutePath());
            marquerModifie();
        }
    }

    private void lancerSauvegarde() {
        Path dossier = dossierSauvegarde();
        if (dossier == null) {
            return;
        }
        setBackupBusy(true, "Sauvegarde en cours…");
        Task<Path> tache = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return BackupService.get().backupNow(dossier);
            }
        };
        tache.setOnSucceeded(e -> {
            setBackupBusy(false, null);
            Path fichier = tache.getValue();
            lblBackup.setText("Sauvegarde créée : " + fichier.toAbsolutePath());
            ToastService.get().success("Sauvegarde", "Fichier créé : " + fichier.getFileName());
        });
        tache.setOnFailed(e -> {
            setBackupBusy(false, null);
            String message = message(tache.getException());
            lblBackup.setText("Échec de la sauvegarde : " + message);
            ToastService.get().error("Sauvegarde impossible", message);
        });
        Thread fil = new Thread(tache, "backup-db");
        fil.setDaemon(true);
        fil.start();
    }

    private void lancerRestauration() {
        FileChooser choix = new FileChooser();
        choix.setTitle("Choisir une sauvegarde SQLite");
        choix.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Sauvegardes SQLite", "*.db", "*.sqlite", "*.sqlite3"));
        choix.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Tous les fichiers", "*.*"));
        Path dossier = dossierSauvegarde();
        if (dossier != null && Files.isDirectory(dossier)) {
            choix.setInitialDirectory(dossier.toFile());
        }
        File fichier = choix.showOpenDialog(fenetre());
        if (fichier == null) {
            return;
        }
        DialogService.get().confirm("Restaurer la base de données",
                "Cette opération remplace toutes les données actuelles par celles du fichier « "
                        + fichier.getName() + " ». Une sauvegarde de sécurité sera créée avant. "
                        + "N'utilisez pas l'application pendant la restauration. Continuer ?",
                "Restaurer", true).thenAccept(confirme -> {
            if (confirme) {
                restaurer(fichier.toPath(), dossier);
            }
        });
    }

    private void restaurer(Path fichier, Path dossierSecurite) {
        setBackupBusy(true, "Restauration en cours…");
        Task<BackupService.RestoreResult> tache = new Task<>() {
            @Override
            protected BackupService.RestoreResult call() throws Exception {
                return BackupService.get().restore(fichier, dossierSecurite);
            }
        };
        tache.setOnSucceeded(e -> {
            setBackupBusy(false, null);
            BackupService.RestoreResult resultat = tache.getValue();
            lblBackup.setText("Restauration terminée. Sauvegarde de sécurité : "
                    + resultat.safetyBackup().getFileName()
                    + ". Redémarrez l'application pour recharger les données.");
            ToastService.get().success("Restauration terminée",
                    "Redémarrez l'application pour recharger les données.");
        });
        tache.setOnFailed(e -> {
            setBackupBusy(false, null);
            String message = message(tache.getException());
            lblBackup.setText("Échec de la restauration : " + message);
            ToastService.get().error("Restauration impossible", message);
        });
        Thread fil = new Thread(tache, "restore-db");
        fil.setDaemon(true);
        fil.start();
    }

    private Path dossierSauvegarde() {
        String texte = fBackupDir.textValue();
        try {
            Path dossier = texte == null || texte.isBlank()
                    ? AppConfig.backupDirectory()
                    : Path.of(texte.trim());
            Files.createDirectories(dossier);
            return dossier;
        } catch (Exception e) {
            fBackupDir.setError("Dossier invalide : " + e.getMessage());
            return null;
        }
    }

    private void setBackupBusy(boolean occupe, String message) {
        btnBackup.setDisable(occupe);
        btnRestore.setDisable(occupe);
        if (message != null) {
            lblBackup.setText(message);
        }
    }

    // ---------------------------------------------------------------- utilitaires

    private static VBox carte(String titre, String sousTitre, Node... contenu) {
        Label titreNoeud = new Label(titre);
        titreNoeud.getStyleClass().add("card-title");
        VBox entete = new VBox(3, titreNoeud);
        if (sousTitre != null && !sousTitre.isBlank()) {
            Label sousTitreNoeud = new Label(sousTitre);
            sousTitreNoeud.getStyleClass().add("card-subtitle");
            sousTitreNoeud.setWrapText(true);
            entete.getChildren().add(sousTitreNoeud);
        }
        VBox boite = new VBox(14);
        boite.getStyleClass().add("card");
        boite.getChildren().add(entete);
        boite.getChildren().addAll(contenu);
        return boite;
    }

    private static HBox ligneToggle(String titre, String description, ToggleSwitch toggle) {
        Label titreNoeud = new Label(titre);
        titreNoeud.getStyleClass().add("settings-row-title");
        Label descriptionNoeud = new Label(description);
        descriptionNoeud.getStyleClass().add("muted");
        descriptionNoeud.setWrapText(true);
        VBox textes = new VBox(2, titreNoeud, descriptionNoeud);
        HBox.setHgrow(textes, Priority.ALWAYS);
        HBox ligne = new HBox(14, textes, toggle);
        ligne.getStyleClass().add("settings-row");
        ligne.setAlignment(Pos.CENTER_LEFT);
        return ligne;
    }

    private static HBox ligneChampBouton(FormField champ, Button bouton) {
        HBox ligne = new HBox(8, champ, bouton);
        ligne.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(champ, Priority.ALWAYS);
        return ligne;
    }

    private Window fenetre() {
        return btnSave.getScene() == null ? null : btnSave.getScene().getWindow();
    }

    private static String premier(Map<String, String> valeurs, String defaut, String... cles) {
        for (String cle : cles) {
            String valeur = valeurs.get(cle);
            if (valeur != null && !valeur.isBlank()) {
                return valeur;
            }
        }
        return defaut;
    }

    private static boolean bool(Map<String, String> valeurs, String cle, boolean defaut) {
        String valeur = valeurs.get(cle);
        return valeur == null || valeur.isBlank() ? defaut : Boolean.parseBoolean(valeur);
    }

    private static String dossierParDefaut() {
        return AppConfig.backupDirectory().toString();
    }

    private static String message(Throwable erreur) {
        if (erreur == null) {
            return "Erreur inconnue.";
        }
        String message = erreur.getMessage();
        return message == null || message.isBlank() ? erreur.getClass().getSimpleName() : message;
    }
}
