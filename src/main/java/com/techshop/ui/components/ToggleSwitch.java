package com.techshop.ui.components;

import com.techshop.util.Format;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Interrupteur (toggle) réutilisable (SPEC §2) : pastille blanche sur piste
 * arrondie, état actif en indigo.
 */
public class ToggleSwitch extends StackPane {

    private final javafx.scene.layout.Region thumb;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private static final double THUMB = 16;
    private static final double TRAVEL = 40 - 6 - THUMB; // piste 40, marge 3

    public ToggleSwitch() {
        getStyleClass().add("switch");
        setAlignment(Pos.CENTER_LEFT);
        setPrefSize(40, 22);

        thumb = new javafx.scene.layout.Region();
        thumb.getStyleClass().add("switch-thumb");
        thumb.setPrefSize(THUMB, THUMB);
        thumb.setMaxSize(THUMB, THUMB);

        getChildren().add(thumb);
        setOnMouseClicked(e -> setSelected(!isSelected()));

        selected.addListener((obs, old, sel) -> {
            getStyleClass().remove("on");
            if (sel) {
                getStyleClass().add("on");
            }
            // Déplacement doux de la pastille (animation simple autorisée)
            Timeline t = new Timeline(new KeyFrame(Duration.millis(140),
                    new KeyValue(thumb.translateXProperty(), sel ? TRAVEL : 0)));
            t.play();
        });
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean v) {
        selected.set(v);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }
}
