package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.service.AuthService;
import com.techshop.ui.Icons;
import com.techshop.ui.Overlays;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran de connexion (SPEC §4.1) : écran scindé, marque à gauche, formulaire à
 * droite, message d'erreur sous le formulaire et blocage temporaire après cinq
 * échecs. Les règles d'authentification sont dans {@link AuthService}.
 */
public class LoginController {

    @FXML
    private StackPane overlayHost;
    @FXML
    private StackPane brandIcon;
    @FXML
    private VBox brandFeatures;
    @FXML
    private VBox formBox;
    @FXML
    private Label errorLabel;

    private FormField champIdentifiant;
    private FormField champMotDePasse;
    private Button boutonConnexion;

    @FXML
    private void initialize() {
        // Toasts/dialogues sur le calque de l'écran (ex. messages d'erreur)
        Overlays.attach(overlayHost);

        brandIcon.getChildren().setAll(Icons.feather("smartphone", 26));
        construireArguments();
        construireFormulaire();

        Platform.runLater(() -> champIdentifiant.requestFocus());
    }

    /** Arguments de la marque (colonne de gauche). */
    private void construireArguments() {
        ajouterArgument("check-circle", "Gestion du stock et des ventes en temps réel");
        ajouterArgument("check-circle", "Suivi des réparations et des garanties");
        ajouterArgument("check-circle", "Caisse, rapports et droits par rôle");
    }

    private void ajouterArgument(String icone, String texte) {
        Label label = new Label(texte);
        label.getStyleClass().add("auth-feature-text");
        label.setWrapText(true);
        HBox ligne = new HBox(10, Icons.feather(icone, 16), label);
        ligne.getStyleClass().add("auth-feature");
        brandFeatures.getChildren().add(ligne);
    }

    /** Champs du formulaire de connexion. */
    private void construireFormulaire() {
        champIdentifiant = Fields.text("Identifiant", "ex. admin");
        champMotDePasse = Fields.password("Mot de passe");

        boutonConnexion = Buttons.primary("Se connecter", "log-in");
        boutonConnexion.getStyleClass().add("auth-submit");
        boutonConnexion.setMaxWidth(Double.MAX_VALUE);
        boutonConnexion.setDefaultButton(true);
        boutonConnexion.setOnAction(e -> seConnecter());

        formBox.getChildren().addAll(champIdentifiant, champMotDePasse, boutonConnexion);
    }

    private void seConnecter() {
        masquerErreur();
        AuthService.Result resultat = AuthService.get()
                .login(champIdentifiant.textValue(), champMotDePasse.textValue());

        switch (resultat.status()) {
            case SUCCESS -> AppShell.showMain();
            case MUST_CHANGE_PASSWORD -> AppShell.showChangePassword();
            default -> afficherErreur(resultat.message());
        }
    }

    private void afficherErreur(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void masquerErreur() {
        errorLabel.setText(null);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
