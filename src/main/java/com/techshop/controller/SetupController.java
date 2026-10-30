package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.service.SetupService;
import com.techshop.ui.Branding;
import com.techshop.ui.Icons;
import com.techshop.ui.Overlays;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.util.ValidationException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Assistant local de première configuration. */
public class SetupController {

    @FXML private StackPane overlayHost;
    @FXML private StackPane brandIcon;
    @FXML private VBox formBox;
    @FXML private Label errorLabel;

    private FormField storeName;
    private FormField address;
    private FormField phone;
    private FormField email;
    private FormField taxId;
    private FormField currency;
    private FormField vat;
    private FormField backupDir;
    private FormField ownerName;
    private FormField username;
    private FormField password;
    private FormField confirmation;
    private Button submit;

    @FXML
    private void initialize() {
        Overlays.attach(overlayHost);
        Branding.mount(brandIcon, 36);
        buildForm();
        Platform.runLater(() -> storeName.requestFocus());
    }

    private void buildForm() {
        storeName = Fields.text("Nom du magasin *", "Ex. TechShop Alger");
        address = Fields.text("Adresse", "Adresse commerciale");
        phone = Fields.text("Téléphone", "0X XX XX XX XX");
        email = Fields.text("Courriel", "contact@magasin.dz");
        taxId = Fields.text("NIF / RC / NIS", "Optionnel");
        currency = Fields.text("Devise *", "DZD");
        currency.setValue("DZD");
        vat = Fields.number("TVA (%)", "19");
        vat.setValue("19");
        backupDir = Fields.text("Dossier de sauvegarde", "Optionnel");
        ownerName = Fields.text("Nom complet du gérant *", "Ex. Nadia Belkacem");
        username = Fields.text("Identifiant *", "3 à 60 caractères");
        password = Fields.password("Mot de passe *");
        confirmation = Fields.password("Confirmer le mot de passe *");

        submit = Buttons.primary("Créer l'installation", "check-circle");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> initializeApplication());

        formBox.getChildren().setAll(
                section("1. Informations du magasin"),
                storeName, address, phone, email, taxId, currency, vat, backupDir,
                section("2. Compte Gérant"),
                ownerName, username, password, confirmation, submit);
    }

    private Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("auth-title");
        return label;
    }

    private void initializeApplication() {
        hideError();
        submit.setDisable(true);
        try {
            String recovery = SetupService.get().initialize(new SetupService.SetupData(
                    storeName.textValue(), address.textValue(), phone.textValue(), email.textValue(),
                    taxId.textValue(), currency.textValue(), vat.textValue(), backupDir.textValue(),
                    ownerName.textValue(), username.textValue(), password.textValue(),
                    confirmation.textValue()));
            showRecovery(recovery);
        } catch (ValidationException e) {
            showError(e.getMessage());
            submit.setDisable(false);
        }
    }

    private void showRecovery(String key) {
        formBox.getChildren().clear();
        Label title = new Label("Clé de récupération");
        title.getStyleClass().add("auth-title");
        Label message = new Label("Conservez cette clé hors de l'ordinateur. "
                + "Elle sera affichée une seule fois et permet de récupérer le compte Gérant.");
        message.setWrapText(true);
        Label value = new Label(key);
        value.getStyleClass().add("auth-title");
        value.setWrapText(true);
        Button copy = Buttons.secondary("Copier la clé");
        copy.setGraphic(Icons.feather("copy", 15));
        copy.setOnAction(e -> {
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(key);
            clipboard.setContent(content);
        });
        Button save = Buttons.secondary("Enregistrer");
        save.setGraphic(Icons.feather("save", 15));
        save.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Enregistrer la clé de récupération");
            chooser.setInitialFileName("cle-recuperation-techshop.txt");
            var file = chooser.showSaveDialog(formBox.getScene().getWindow());
            if (file != null) {
                try {
                    java.nio.file.Files.writeString(file.toPath(), key);
                } catch (java.io.IOException ex) {
                    showError("Enregistrement impossible : " + ex.getMessage());
                }
            }
        });
        Button print = Buttons.secondary("Imprimer");
        print.setGraphic(Icons.feather("printer", 15));
        print.setOnAction(e -> {
            javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
            if (job != null && job.showPrintDialog(formBox.getScene().getWindow())) {
                if (job.printPage(new Label("Clé de récupération TechShop Manager : " + key))) {
                    job.endJob();
                }
            }
        });
        Button finish = Buttons.primary("Continuer vers la connexion", "log-in");
        finish.setOnAction(e -> AppShell.showLogin());
        formBox.getChildren().setAll(title, message, value, new HBox(8, copy, save, print), finish);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setText(null);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
