package com.techshop.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Pied de tableau stylé : compteur de résultats à gauche, pagination à droite
 * (SPEC §2 : « pagination en pied, compteur de résultats »).
 * Le branchement effectif sur les données viendra avec le module concerné.
 */
public class TableFooter extends HBox {

    private final Label countLabel = new Label();
    private final Label pageLabel = new Label();
    private final javafx.scene.control.Button prev;
    private final javafx.scene.control.Button next;

    public TableFooter() {
        getStyleClass().add("table-footer");
        setAlignment(Pos.CENTER_LEFT);

        countLabel.getStyleClass().add("table-count");
        countLabel.setText("0 résultat");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        prev = Buttons.iconCompact("chevron-left", "Page précédente");
        prev.setDisable(true);
        pageLabel.getStyleClass().add("table-count");
        pageLabel.setText("Page 1 / 1");
        next = Buttons.iconCompact("chevron-right", "Page suivante");
        next.setDisable(true);

        HBox pager = new HBox(8, prev, pageLabel, next);
        pager.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(countLabel, spacer, pager);
    }

    public void setResultCount(int n) {
        countLabel.setText(n <= 1 ? n + " résultat" : n + " résultats");
    }

    public void setPage(int page, int totalPages) {
        pageLabel.setText("Page " + Math.max(1, page) + " / " + Math.max(1, totalPages));
        prev.setDisable(page <= 1);
        next.setDisable(page >= totalPages);
    }

    public void setPrevAction(Runnable r) {
        prev.setOnAction(e -> r.run());
    }

    public void setNextAction(Runnable r) {
        next.setOnAction(e -> r.run());
    }
}
