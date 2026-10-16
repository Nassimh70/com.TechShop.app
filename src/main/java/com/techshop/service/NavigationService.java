package com.techshop.service;

import com.techshop.model.NavItem;
import com.techshop.ui.components.ScreenInitializer;
import com.techshop.util.Fxmls;
import com.techshop.util.Loaded;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Navigateur d'écrans central (SPEC §1) : charge les vues FXML dans la zone de
 * contenu de la fenêtre principale et notifie les changements de page
 * (titre, fil d'Ariane, entrée active du menu).
 */
public final class NavigationService {

    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);
    private static final NavigationService INSTANCE = new NavigationService();

    /** Vue générique des modules non encore réalisés. */
    public static final String UNDER_CONSTRUCTION = "screens/under-construction";

    private javafx.scene.layout.StackPane contentHost;
    private NavItem current;
    private Consumer<NavItem> onScreenChanged = item -> {
    };

    private NavigationService() {
    }

    public static NavigationService get() {
        return INSTANCE;
    }

    /** Branché par MainController au démarrage. */
    public void init(javafx.scene.layout.StackPane host) {
        this.contentHost = host;
    }

    public void setOnScreenChanged(Consumer<NavItem> listener) {
        this.onScreenChanged = listener;
    }

    public NavItem getCurrent() {
        return current;
    }

    /** Navigue vers un écran du menu. */
    public void navigate(NavItem item) {
        navigate(item, Map.of());
    }

    public void navigate(String screenId) {
        NavItem item = MenuRegistry.byId(screenId);
        if (item != null) {
            navigate(item);
        } else {
            log.warn("Écran inconnu : {}", screenId);
        }
    }

    /** Navigue vers un écran avec des paramètres (fiche, onglet, etc.). */
    public void navigate(NavItem item, Map<String, Object> params) {
        if (contentHost == null) {
            throw new IllegalStateException("NavigationService non initialisé (contentHost manquant)");
        }
        if (item == null) {
            return;
        }
        String fxml = item.getFxml() != null ? item.getFxml() : UNDER_CONSTRUCTION;
        Map<String, Object> p = new HashMap<>(params);
        p.putIfAbsent("navItem", item);
        p.putIfAbsent("title", item.getTitle());
        if (item.getFxml() == null) {
            p.putIfAbsent("message", "L’écran « " + item.getTitle()
                    + " » sera disponible dans une prochaine phase.");
        }
        try {
            Loaded loaded = Fxmls.load(fxml);
            if (loaded.controller() instanceof ScreenInitializer si) {
                si.init(p);
            }
            contentHost.getChildren().setAll(loaded.root());
            current = item;
            onScreenChanged.accept(item);
            log.debug("Navigation -> {} ({})", item.getId(), fxml);
        } catch (Exception e) {
            log.error("Échec de chargement de l'écran {}", item.getId(), e);
            // Repli propre : message dans la zone de contenu au lieu d'un crash
            var fallback = com.techshop.ui.components.EmptyState.error(
                    "Écran indisponible", "Impossible d’ouvrir « " + item.getTitle() + " ».\n"
                    + e.getMessage());
            contentHost.getChildren().setAll(fallback);
        }
    }

    /** Charge un nœud arbitraire dans la zone de contenu (cas particuliers). */
    public void showCustom(Parent node) {
        contentHost.getChildren().setAll(node);
        current = null;
        onScreenChanged.accept(null);
    }
}
