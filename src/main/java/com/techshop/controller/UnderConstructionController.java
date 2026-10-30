package com.techshop.controller;

import com.techshop.service.NavigationService;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.ScreenInitializer;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Écran d'attente propre pour les modules non encore développés (SPEC §4).
 * Affiche « Module en cours de construction » avec un retour au tableau de bord.
 */
public class UnderConstructionController implements ScreenInitializer {

    @FXML
    private VBox content;

    @Override
    public void init(Map<String, Object> params) {
        String title = (String) params.getOrDefault("title", "Module en cours de construction");
        String message = (String) params.getOrDefault("message",
                "Cet écran sera disponible dans une prochaine phase de développement.");

        EmptyState state = EmptyState.module(title, message);
        state.withAction("Retour au tableau de bord",
                () -> NavigationService.get().navigate("dashboard"));

        // Carte centrée, aérée
        VBox card = new VBox(state);
        card.getStyleClass().add("card");
        card.setAlignment(javafx.geometry.Pos.CENTER);

        content.getChildren().setAll(card);
    }
}
