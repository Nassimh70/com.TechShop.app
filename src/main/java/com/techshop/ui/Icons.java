package com.techshop.ui;

import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Fabrique d'icônes Feather (Ikonli). Literal au format « fth-home »
 * (préfixe Feather d'Ikonli, cf. FeatherIkonHandler.supports).
 */
public final class Icons {

    /** Préfixe Feather d'Ikonli. */
    private static final String FTH = "fth-";

    private Icons() {
    }

    public static FontIcon feather(String name) {
        return new FontIcon(FTH + name);
    }

    public static FontIcon feather(String name, double size) {
        FontIcon icon = feather(name);
        icon.setIconSize((int) Math.round(size));
        return icon;
    }

    public static FontIcon feather(String name, double size, String hexColor) {
        FontIcon icon = feather(name, size);
        icon.setIconColor(Color.web(hexColor));
        return icon;
    }
}
