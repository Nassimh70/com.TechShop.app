package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.config.AppConfig;
import com.techshop.config.DatabaseManager;
import com.techshop.ui.Icons;
import com.techshop.ui.Overlays;
import com.techshop.ui.components.ToastService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Écran d'erreur de connexion à la base SQLite (SPEC §5, §7) : message clair,
 * bouton « Réessayer », emplacement du fichier et accès au dossier des données.
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

    @FXML
    private void initialize() {
        Overlays.attach(overlayHost);
        errorIcon.getChildren().setAll(Icons.feather("database", 30));

        String erreur = DatabaseManager.getLastError();
        messageLabel.setText(erreur == null
                ? "La base de données n'est pas accessible."
                : erreur);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(560);

        attemptsLabel.setText("Fichier attendu : " + AppConfig.databasePath());

        retryBtn.setGraphic(Icons.feather("refresh-cw", 16));
        retryBtn.setOnAction(e -> AppShell.retry());
        settingsBtn.setGraphic(Icons.feather("folder-open", 16));
        settingsBtn.setOnAction(e -> ouvrirDossierDonnees());
    }

    private void ouvrirDossierDonnees() {
        Path dossier = AppConfig.databasePath().getParent();
        try {
            Files.createDirectories(dossier);
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dossier.toFile());
            } else {
                ToastService.get().info("Dossier des données",
                        "Le fichier de base se trouve dans : " + dossier.toAbsolutePath());
            }
        } catch (Exception e) {
            ToastService.get().info("Dossier des données",
                    "Le fichier de base se trouve dans : " + dossier.toAbsolutePath()
                            + " (ouverture automatique impossible : " + e.getMessage() + ")");
        }
    }
}
