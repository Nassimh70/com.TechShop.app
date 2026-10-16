package com.techshop.ui.components;

import javafx.scene.control.Label;

/**
 * Badge de statut arrondi (SPEC §2) : succès, alerte, erreur, info, neutre.
 */
public class Badge extends Label {

    public enum BadgeType {
        SUCCESS("badge-success"), WARNING("badge-warning"), DANGER("badge-danger"),
        INFO("badge-info"), NEUTRAL("badge-neutral");

        private final String css;

        BadgeType(String css) {
            this.css = css;
        }

        public String css() {
            return css;
        }
    }

    public Badge(String text, BadgeType type) {
        super(text);
        getStyleClass().addAll("badge", type.css());
    }

    public static Badge of(String text, BadgeType type) {
        return new Badge(text, type);
    }
}
