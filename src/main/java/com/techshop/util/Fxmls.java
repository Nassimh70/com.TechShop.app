package com.techshop.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/** Chargeur FXML central : chemins relatifs à /fxml, UTF-8, erreurs explicites. */
public final class Fxmls {

    private Fxmls() {
    }

    /** Résout une ressource FXML (« screens/dashboard » -> /fxml/screens/dashboard.fxml). */
    public static URL url(String name) {
        String path = name.endsWith(".fxml") ? name : name + ".fxml";
        URL url = Fxmls.class.getResource("/fxml/" + path);
        if (url == null) {
            throw new IllegalStateException("Vue FXML introuvable : /fxml/" + path);
        }
        return url;
    }

    /** Charge une vue et retourne la racine + le contrôleur associé. */
    public static Loaded load(String name) {
        try {
            FXMLLoader loader = new FXMLLoader(url(name), bundle());
            Parent root = loader.load();
            return new Loaded(root, loader.getController());
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger la vue « " + name + " »", e);
        }
    }

    /** Bundle de textes français. */
    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle("i18n.messages_fr");
    }
}
