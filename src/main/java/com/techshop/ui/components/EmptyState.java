package com.techshop.ui.components;

import com.techshop.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * État vide : icône + titre + message + bouton d'action optionnel.
 * Utilisé pour les modules « en cours de construction » et les listes vides.
 */
public final class EmptyState extends VBox {

    private final Label title = new Label();
    private final Label message = new Label();

    private EmptyState(String iconFeather, String titleText, String messageText) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);

        var icon = Icons.feather(iconFeather, 40);
        icon.getStyleClass().add("empty-icon");

        title.setText(titleText);
        title.getStyleClass().add("empty-title");

        message.setText(messageText);
        message.getStyleClass().add("empty-msg");
        message.setWrapText(true);
        message.setMaxWidth(420);

        getChildren().addAll(icon, title, message);
    }

    public static EmptyState module(String title, String message) {
        return new EmptyState("tool", title, message);
    }

    public static EmptyState error(String title, String message) {
        return new EmptyState("alert-triangle", title, message);
    }

    public static EmptyState of(String icon, String title, String message) {
        return new EmptyState(icon, title, message);
    }

    /** Ajoute un bouton d'action sous le message. */
    public EmptyState withAction(String label, Runnable action) {
        Button b = Buttons.primary(label);
        b.setOnAction(e -> action.run());
        getChildren().add(b);
        return this;
    }

    /** Ajoute un nœud arbitraire en bas (bouton secondaire, lien...). */
    public EmptyState withExtra(Node node) {
        getChildren().add(node);
        return this;
    }
}
