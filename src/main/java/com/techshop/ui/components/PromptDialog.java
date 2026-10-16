package com.techshop.ui.components;

import com.techshop.util.ValidationException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Petite invite de saisie (SPEC §2) : un seul champ dans une carte modale, avec
 * message d'erreur sous le champ. Le dialogue ne se ferme que si l'action
 * réussit, ce qui permet d'afficher les erreurs de validation métier.
 */
public final class PromptDialog {

    private PromptDialog() {
    }

    /** Saisie d'un texte court ; {@code action} peut lever une ValidationException. */
    public static void text(String title, String icon, String label, String initial,
                            String promptText, Consumer<String> action) {
        FormField champ = Fields.text(label, promptText);
        if (initial != null) {
            champ.setValue(initial);
        }

        Label erreur = new Label();
        erreur.getStyleClass().add("field-error-msg");
        erreur.setWrapText(true);
        erreur.setManaged(false);
        erreur.setVisible(false);

        VBox corps = new VBox(10, champ, erreur);
        corps.setPrefWidth(360);

        Button annuler = Buttons.secondary("Annuler");
        Button valider = Buttons.primary("Enregistrer", "check");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card(title, icon, corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, true);

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            champ.clearError();
            erreur.setManaged(false);
            erreur.setVisible(false);
            try {
                action.accept(champ.textValue());
                handle.close();
            } catch (ValidationException ex) {
                champ.setError(ex.getMessage());
                erreur.setText(ex.getMessage());
                erreur.setManaged(true);
                erreur.setVisible(true);
            } catch (RuntimeException ex) {
                ToastService.get().error(title, ex.getMessage());
            }
        });
        champ.styledControl().requestFocus();
    }
}
