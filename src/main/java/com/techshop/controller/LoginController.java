package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.service.AuthService;
import com.techshop.service.SetupService;
import com.techshop.ui.Branding;
import com.techshop.ui.Icons;
import com.techshop.ui.Overlays;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
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
    private Button boutonRecuperation;

    @FXML
    private void initialize() {
        // Toasts/dialogues sur le calque de l'écran (ex. messages d'erreur)
        Overlays.attach(overlayHost);
        DialogService.get().init(overlayHost);

        Branding.mount(brandIcon, 36);
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

        boutonRecuperation = Buttons.link("Mot de passe oublié ?");
        boutonRecuperation.setOnAction(e -> recupererMotDePasse());
        formBox.getChildren().addAll(champIdentifiant, champMotDePasse,
                boutonConnexion, boutonRecuperation);
    }

    private void recupererMotDePasse() {
        FormField cle = Fields.text("Clé de récupération", "XXXXXX-XXXXXX-XXXXXX-XXXXXX");
        FormField nouveau = Fields.password("Nouveau mot de passe");
        FormField confirmation = Fields.password("Confirmation");
        VBox corps = new VBox(12, cle, nouveau, confirmation);
        DialogService.get().form("Récupération hors ligne", corps, "Réinitialiser")
                .thenAccept(ok -> {
                    if (!Boolean.TRUE.equals(ok)) {
                        return;
                    }
                    try {
                        String next = SetupService.get().resetPasswordWithRecoveryKey(
                                cle.textValue(), nouveau.textValue(), confirmation.textValue());
                        Platform.runLater(() -> afficherCleRegenerée(next));
                    } catch (RuntimeException e) {
                        afficherErreur(e.getMessage());
                    }
                });
    }

    private void afficherCleRegenerée(String key) {
        VBox corps = new VBox(12,
                new Label("Votre mot de passe a été réinitialisé."),
                new Label("Nouvelle clé de récupération (à conserver) :"),
                new Label(key));
        DialogService.get().form("Nouvelle clé de récupération", corps, "Fermer");
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
