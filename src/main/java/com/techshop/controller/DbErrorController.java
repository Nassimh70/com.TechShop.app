package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.config.AppConfig;
import com.techshop.config.DatabaseManager;
import com.techshop.ui.Icons;
import com.techshop.ui.Overlays;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran d'erreur de connexion à la base (SPEC §5, §7) : message clair, bouton
 * « Réessayer » et accès aux réglages de connexion (hôte, port, base, utilisateur,
 * mot de passe), enregistrés dans le fichier externe config/db.properties.
 */
public class DbErrorController {

    @FXML
    private StackPane overlayHost;
    @FXML
    private StackPane errorIcon;
    @FXML
    private Label messageLabel;
    @FXML
    private Button retryBtn;
    @FXML
    private Button settingsBtn;
    @FXML
    private Label attemptsLabel;

    private FormField champHote;
    private FormField champPort;
    private FormField champBase;
    private FormField champUtilisateur;
    private FormField champMotDePasse;

    @FXML
    private void initialize() {
        Overlays.attach(overlayHost);
        errorIcon.getChildren().setAll(Icons.feather("database", 30));

        String erreur = DatabaseManager.getLastError();
        messageLabel.setText(erreur == null
                ? "La base de données n'est pas joignable."
                : erreur);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(560);

        attemptsLabel.setText("Serveur attendu : " + AppConfig.dbHost() + ":" + AppConfig.dbPort()
                + " — base « " + AppConfig.dbName() + " » (utilisateur « " + AppConfig.dbUser() + " »)");

        retryBtn.setGraphic(Icons.feather("refresh-cw", 16));
        retryBtn.setOnAction(e -> AppShell.retry());
        settingsBtn.setGraphic(Icons.feather("settings", 16));
        settingsBtn.setOnAction(e -> ouvrirReglages());
    }

    private void ouvrirReglages() {
        champHote = Fields.text("Hôte", "localhost");
        champPort = Fields.number("Port", "3306");
        champBase = Fields.text("Base de données", "techshop");
        champUtilisateur = Fields.text("Utilisateur", "root");
        champMotDePasse = Fields.password("Mot de passe");

        champHote.setValue(AppConfig.dbHost());
        champPort.setValue(String.valueOf(AppConfig.dbPort()));
        champBase.setValue(AppConfig.dbName());
        champUtilisateur.setValue(AppConfig.dbUser());
        champMotDePasse.setValue(AppConfig.dbPassword());

        VBox body = new VBox(12, champHote, champPort, champBase, champUtilisateur, champMotDePasse);
        body.setPrefWidth(400);

        DialogService.get().form("Réglages de connexion", body, "Enregistrer et réessayer")
                .thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        enregistrer();
                    }
                });
    }

    private void enregistrer() {
        String hote = valeur(champHote, AppConfig.dbHost());
        int port = parseInt(valeur(champPort, String.valueOf(AppConfig.dbPort())), AppConfig.dbPort());
        String base = valeur(champBase, AppConfig.dbName());
        String utilisateur = valeur(champUtilisateur, AppConfig.dbUser());
        String motDePasse = champMotDePasse.textValue();

        AppConfig.saveConnection(hote, port, base, utilisateur, motDePasse);
        AppShell.retry();
    }

    private static String valeur(FormField champ, String defaut) {
        String v = champ == null ? null : champ.textValue();
        return (v == null || v.isBlank()) ? defaut : v.trim();
    }

    private static int parseInt(String valeur, int defaut) {
        try {
            return Integer.parseInt(valeur);
        } catch (NumberFormatException e) {
            return defaut;
        }
    }
}
