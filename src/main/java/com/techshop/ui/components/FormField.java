package com.techshop.ui.components;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Champ groupé : libellé (12 px majuscules espacées), contrôle, message
 * d'erreur sous le champ (bordure rouge + message, SPEC §2).
 */
public class FormField extends VBox {

    private final Label labelNode;
    private final Node control;
    private final Control styledControl; // contrôle porteur des styles field:*
    private final Label message = new Label();

    public FormField(String label, Node control, Control styledControl) {
        setSpacing(6);
        this.control = control;
        this.styledControl = styledControl;

        if (label != null && !label.isBlank()) {
            labelNode = new Label(label.toUpperCase());
            labelNode.getStyleClass().add("field-label");
            getChildren().add(labelNode);
        } else {
            labelNode = null;
        }
        getChildren().add(control);

        message.getStyleClass().add("field-msg");
        message.setManaged(false);
        message.setVisible(false);
        getChildren().add(message);
    }

    public Node control() {
        return control;
    }

    /** Contrôle de saisie (TextField, ComboBox... selon le type de champ). */
    public Control styledControl() {
        return styledControl;
    }

    /** Alias pratique : contrôle stylé porteur des états field:*. */
    public Control field() {
        return styledControl;
    }

    /** Affiche un message d'erreur sous le champ. */
    public void setError(String msg) {
        if (!styledControl.getStyleClass().contains("field:error")) {
            styledControl.getStyleClass().add("field:error");
        }
        message.setText(msg);
        message.getStyleClass().remove("field-msg");
        if (!message.getStyleClass().contains("field-error-msg")) {
            message.getStyleClass().add("field-error-msg");
        }
        message.setManaged(true);
        message.setVisible(true);
    }

    /** Retire l'état d'erreur. */
    public void clearError() {
        styledControl.getStyleClass().remove("field:error");
        message.setText(null);
        message.getStyleClass().remove("field-error-msg");
        message.setManaged(false);
        message.setVisible(false);
    }

    /** Raccourci : valeur texte du contrôle s'il s'agit d'un champ de saisie. */
    public String textValue() {
        if (styledControl instanceof javafx.scene.control.TextInputControl tic) {
            return tic.getText();
        }
        if (styledControl instanceof javafx.scene.control.ComboBox<?> combo
                && combo.getValue() != null) {
            return combo.getValue().toString();
        }
        return "";
    }

    /** Renseigne la valeur du contrôle (texte ou liste déroulante). */
    public void setValue(String valeur) {
        if (styledControl instanceof javafx.scene.control.TextInputControl tic) {
            tic.setText(valeur == null ? "" : valeur);
        } else if (styledControl instanceof javafx.scene.control.ComboBox combo) {
            combo.setValue(valeur);
        }
    }
}
