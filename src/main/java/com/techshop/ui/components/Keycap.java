package com.techshop.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Petite touche de raccourci clavier (« F10 », « Ctrl+K »...) affichée à côté
 * des actions principales (SPEC §2).
 */
public final class Keycap {

    private Keycap() {
    }

    public static Label of(String key) {
        Label l = new Label(key);
        l.getStyleClass().add("keycap");
        return l;
    }

    /** Libellé + touche côte à côte. */
    public static HBox hint(String text, String key) {
        HBox box = new HBox(8, new Label(text), of(key));
        box.getStyleClass().add("keycap-hint");
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }
}
