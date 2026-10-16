package com.techshop.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/**
 * Carte statistique (SPEC §2) : libellé majuscule, grand chiffre 28 px,
 * variation ▲ vert / ▼ rouge, mini-courbe optionnelle (sparkline décorative).
 */
public class StatCard extends VBox {

    public enum Trend {UP, DOWN, NEUTRAL}

    private final Label valueNode = new Label();
    private final Label variationNode = new Label();

    public StatCard(String label, String value) {
        this(label, value, null, Trend.NEUTRAL, false);
    }

    public StatCard(String label, String value, String variation, Trend trend, boolean withSpark) {
        getStyleClass().add("stat-card");
        setSpacing(2);

        Label l = new Label(label.toUpperCase());
        l.getStyleClass().add("stat-label");

        valueNode.setText(value);
        valueNode.getStyleClass().add("stat-value");

        getChildren().addAll(l, valueNode);

        if (variation != null) {
            switch (trend) {
                case UP -> variationNode.getStyleClass().add("stat-var-up");
                case DOWN -> variationNode.getStyleClass().add("stat-var-down");
                default -> variationNode.getStyleClass().add("stat-var-neutral");
            }
            variationNode.getStyleClass().add("stat-var");
            String arrow = trend == Trend.UP ? "▲ " : trend == Trend.DOWN ? "▼ " : "";
            variationNode.setText(arrow + variation);
            HBox foot = new HBox(variationNode);
            foot.setAlignment(Pos.CENTER_LEFT);
            if (withSpark) {
                Region spark = new Region();
                spark.getStyleClass().add("spark");
                HBox.setHgrow(spark, Priority.SOMETIMES);
                spark.setMaxWidth(Double.MAX_VALUE);
                foot.getChildren().add(spark);
            }
            getChildren().add(foot);
        }
    }

    public void setValue(String v) {
        valueNode.setText(v);
    }

    public void setVariation(String v, Trend trend) {
        variationNode.setText(v);
    }

    /** Variante pratique : variation en pourcentageBigDecimal. */
    public static StatCard of(String label, String value, BigDecimal pctVariation) {
        if (pctVariation == null) {
            return new StatCard(label, value);
        }
        Trend t = pctVariation.signum() > 0 ? Trend.UP : pctVariation.signum() < 0
                ? Trend.DOWN : Trend.NEUTRAL;
        return new StatCard(label, value, com.techshop.util.Format.percent(pctVariation), t, true);
    }
}
