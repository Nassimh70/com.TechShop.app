package com.techshop.ui.components;

import java.util.Map;

/**
 * Contrôleur de page pouvant recevoir des paramètres au chargement
 * (voir NavigationService.navigate).
 */
public interface ScreenInitializer {

    void init(Map<String, Object> params);
}
