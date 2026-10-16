package com.techshop.ui.components;

import com.techshop.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Boutons réutilisables (SPEC §2) : principal, secondaire, contour, danger,
 * fantôme, icône seule — avec états survol / appuyé / désactivé / focus
 * entièrement gérés par le CSS.
 */
public final class Buttons {

    private Buttons() {
    }

    // ------------------------------- Variantes -------------------------------

    public static Button primary(String text) {
        return build(text, "btn-primary");
    }

    public static Button primary(String text, String iconFeather) {
        return build(text, "btn-primary", iconFeather);
    }

    public static Button secondary(String text) {
        return build(text, "btn-secondary");
    }

    public static Button secondary(String text, String iconFeather) {
        return build(text, "btn-secondary", iconFeather);
    }

    public static Button outline(String text) {
        return build(text, "btn-outline");
    }

    public static Button outline(String text, String iconFeather) {
        return build(text, "btn-outline", iconFeather);
    }

    public static Button danger(String text) {
        return build(text, "btn-danger");
    }

    public static Button danger(String text, String iconFeather) {
        return build(text, "btn-danger", iconFeather);
    }

    public static Button ghost(String text) {
        return build(text, "btn-ghost");
    }

    public static Button ghost(String text, String iconFeather) {
        return build(text, "btn-ghost", iconFeather);
    }

    /** Bouton carré icône seule (36 px). */
    public static Button icon(String iconFeather, String tooltip) {
        Button b = new Button();
        b.getStyleClass().addAll("btn", "btn-icon", "icon-btn");
        b.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        b.setGraphic(Icons.feather(iconFeather, 17));
        if (tooltip != null) {
            b.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        }
        return b;
    }

    /** Bouton icône compact (30 px). */
    public static Button iconCompact(String iconFeather, String tooltip) {
        Button b = icon(iconFeather, tooltip);
        b.getStyleClass().add("btn-compact");
        return b;
    }

    /** Version compacte (30 px) d'un bouton existant. */
    public static Button compact(Button b) {
        b.getStyleClass().add("btn-compact");
        return b;
    }

    /** Bouton fantôme icône seule. */
    public static Button ghostIcon(String iconFeather, String tooltip) {
        Button b = icon(iconFeather, tooltip);
        b.getStyleClass().remove("icon-btn");
        b.getStyleClass().add("btn-ghost");
        return b;
    }

    // --------------------- Action + raccourci clavier affiché -----------------

    /**
     * Bouton avec petite touche de raccourci à côté du libellé,
     * ex. « Encaisser (F10) » (SPEC §2).
     */
    public static HBox actionWithShortcut(String text, String iconFeather, String key,
                                          Runnable action, String variant) {
        Button b = build(text, variant, iconFeather);
        b.setOnAction(e -> action.run());
        if (key == null || key.isBlank()) {
            HBox wrap = new HBox(b);
            wrap.setAlignment(Pos.CENTER_LEFT);
            return wrap;
        }
        // Petite touche affichée à côté de l'action (ex. « Encaisser F10 »)
        HBox wrap = new HBox(8, b, Keycap.of(key));
        wrap.setAlignment(Pos.CENTER_LEFT);
        return wrap;
    }

    // -------------------------------- Interne ---------------------------------

    private static Button build(String text, String... cssAndIcon) {
        List<String> css = new ArrayList<>();
        String icon = null;
        for (String s : cssAndIcon) {
            if (s == null) {
                continue;
            }
            if (s.startsWith("btn-") || s.equals("chip")) {
                css.add(s);
            } else {
                icon = s;
            }
        }
        Button b = new Button(text);
        b.getStyleClass().add("btn");
        b.getStyleClass().addAll(css);
        if (icon != null && !icon.isBlank()) {
            b.setGraphic(Icons.feather(icon, 16));
            b.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        }
        return b;
    }
}
