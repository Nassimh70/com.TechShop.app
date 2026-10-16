package com.techshop.config;

import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * Gestion du thème (clair / sombre) : bascule à chaud sur la Scene courante,
 * préférence enregistrée entre les lancements (SPEC §2).
 */
public final class ThemeManager {

    public enum Theme {LIGHT, DARK}

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String KEY_THEME = "theme";

    private static Theme current = loadSaved();
    private static Scene scene;

    private ThemeManager() {
    }

    /** Applique la feuille de thème à une scène (base.css + thème courant). */
    public static void apply(Scene s) {
        scene = s;
        refresh();
    }

    /** Recharge les feuilles de style de la scène courante. */
    public static void refresh() {
        if (scene == null) {
            return;
        }
        List<String> sheets = List.of(
                resource("/css/base.css"),
                resource(current == Theme.LIGHT ? "/css/theme-light.css" : "/css/theme-dark.css"));
        scene.getStylesheets().setAll(sheets);
        log.info("Thème appliqué : {}", current);
    }

    public static void toggle() {
        setTheme(current == Theme.LIGHT ? Theme.DARK : Theme.LIGHT);
    }

    public static void setTheme(Theme t) {
        current = t;
        PREFS.put(KEY_THEME, t.name());
        refresh();
    }

    public static Theme getTheme() {
        return current;
    }

    public static boolean isDark() {
        return current == Theme.DARK;
    }

    private static Theme loadSaved() {
        String saved = PREFS.get(KEY_THEME, Theme.LIGHT.name());
        try {
            return Theme.valueOf(saved);
        } catch (IllegalArgumentException e) {
            return Theme.LIGHT;
        }
    }

    private static String resource(String path) {
        String url = ThemeManager.class.getResource(path) != null
                ? ThemeManager.class.getResource(path).toExternalForm() : path;
        return url;
    }
}
