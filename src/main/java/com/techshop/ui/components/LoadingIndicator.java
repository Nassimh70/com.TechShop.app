package com.techshop.ui.components;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

/**
 * Indicateur de chargement (SPEC §2) : arc indigo en rotation continue.
 * Utilisé pour les tâches longues (exports, sauvegardes, requêtes lourdes).
 */
public class LoadingIndicator extends StackPane {

    public LoadingIndicator() {
        this(36);
    }

    public LoadingIndicator(double size) {
        getStyleClass().add("loading-indicator");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);

        Arc arc = new Arc(size / 2, size / 2, size / 2 - 4, size / 2 - 4, 90, -270);
        arc.setType(ArcType.OPEN);
        arc.getStyleClass().add("loading-arc");

        getChildren().add(arc);

        RotateTransition spin = new RotateTransition(Duration.millis(900), arc);
        spin.setByAngle(360);
        spin.setCycleCount(RotateTransition.INDEFINITE);
        spin.setInterpolator(Interpolator.LINEAR);
        spin.play();
    }

    /** Indicateur accompagné d'un libellé, prêt à poser dans une carte. */
    public static Region withLabel(String texte) {
        LoadingIndicator spinner = new LoadingIndicator(36);
        javafx.scene.control.Label label = new javafx.scene.control.Label(texte);
        label.getStyleClass().add("loading-label");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(spinner, label);
        box.getStyleClass().add("loading-box");
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }
}
