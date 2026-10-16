package com.techshop.controller;

import com.techshop.app.AppShell;
import com.techshop.model.User;
import com.techshop.service.AuthService;
import com.techshop.service.SessionContext;
import com.techshop.ui.Icons;
import com.techshop.ui.Overlays;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.ValidationException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran de changement de mot de passe obligatoire (SPEC §4.1).
 * Affiché après la première connexion de l'administrateur initial : il doit
 * définir un nouveau mot de passe avant d'accéder à l'application.
 */
public class ChangePasswordController {

    @FXML
    private StackPane overlayHost;
    @FXML
    private StackPane brandIcon;
    @FXML
    private VBox formBox;
    @FXML
    private Label errorLabel;
    @FXML
    private Button logoutBtn;

    private FormField champActuel;
    private FormField champNouveau;
    private FormField champConfirmation;

    @FXML
    private void initialize() {
        Overlays.attach(overlayHost);
        brandIcon.getChildren().setAll(Icons.feather("lock", 26));

        champActuel = Fields.password("Mot de passe actuel");
        champNouveau = Fields.password("Nouveau mot de passe");
        champConfirmation = Fields.password("Confirmer le nouveau mot de passe");

        Button valider = Buttons.primary("Changer le mot de passe", "check");
        valider.getStyleClass().add("auth-submit");
        valider.setMaxWidth(Double.MAX_VALUE);
        valider.setDefaultButton(true);
        valider.setOnAction(e -> changer());

        logoutBtn.setGraphic(Icons.feather("log-out", 15));
        logoutBtn.setText("Se déconnecter");

        formBox.getChildren().addAll(champActuel, champNouveau, champConfirmation, valider);

        Platform.runLater(() -> champActuel.requestFocus());
    }

    private void changer() {        masquerErreur();
        User utilisateur = com.techshop.service.SessionContext.get().getUser();
        if (utilisateur == null) {
            AppShell.showLogin();
            return;
        }
        try {
            AuthService.get().changeOwnPassword(utilisateur.getId(),
                    champActuel.textValue(), champNouveau.textValue(),
                    champConfirmation.textValue());
            ToastService.get().success("Mot de passe",
                    "Votre mot de passe a été modifié avec succès.");
            AppShell.showMain();
        } catch (ValidationException ve) {
            afficherErreur(ve.getMessage());
        }
    }

    @FXML
    private void onLogout(javafx.event.ActionEvent event) {
        com.techshop.service.AuthService.get().logout();
        AppShell.showLogin();
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
