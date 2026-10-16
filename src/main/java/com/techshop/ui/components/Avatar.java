package com.techshop.ui.components;

import com.techshop.util.Format;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * Avatar à initiales colorées (SPEC §2) : couleur déterministe selon le nom
 * parmi une palette sobre.
 */
public final class Avatar extends StackPane {

    /** Palette sobre (accessible, texte blanc). */
    private static final List<String> PALETTE = List.of(
            "#4F46E5", "#0EA5E9", "#16A34A", "#D97706", "#DC2626", "#7C3AED", "#0891B2");

    private Avatar(String fullName, double size) {
        getStyleClass().add("avatar");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setAlignment(Pos.CENTER);

        // Couleur déterministe : somme des codes caractères
        int hash = 0;
        for (char c : (fullName == null ? "?" : fullName).toCharArray()) {
            hash += c;
        }
        setStyle("-fx-background-color: " + PALETTE.get(Math.floorMod(hash, PALETTE.size())) + ";");

        Label initials = new Label(Format.initials(fullName));
        initials.getStyleClass().add("avatar-label");
        initials.setStyle("-fx-font-size: " + Math.round(size * 0.38) + "px;");
        getChildren().add(initials);
    }

    public static Avatar of(String fullName, double size) {
        return new Avatar(fullName, size);
    }
}
