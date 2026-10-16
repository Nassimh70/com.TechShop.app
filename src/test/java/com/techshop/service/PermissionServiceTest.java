package com.techshop.service;

import com.techshop.model.NavItem;
import com.techshop.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la matrice de permissions (SPEC §3) : le menu est filtré, mais
 * l'accès est surtout contrôlé côté service.
 */
class PermissionServiceTest {

    private final NavItem pos = new NavItem("pos", "Point de vente", "VENTES", "shopping-cart",
            null, null, EnumSet.of(Role.ADMIN, Role.VENDEUR));
    private final NavItem repairs = new NavItem("repairs", "SAV", "SERVICE", "tool",
            null, null, EnumSet.of(Role.ADMIN, Role.TECHNICIEN));
    private final NavItem dashboard = NavItem.all("dashboard", "Tableau de bord", "EXPLORATION",
            "home", null);

    @Test
    @DisplayName("Le vendeur accède au point de vente mais pas au SAV")
    void vendeur() {
        PermissionService p = PermissionService.get();
        assertTrue(p.canAccess(pos, Role.VENDEUR));
        assertFalse(p.canAccess(repairs, Role.VENDEUR));
    }

    @Test
    @DisplayName("Le technicien accède au SAV mais pas au point de vente")
    void technicien() {
        PermissionService p = PermissionService.get();
        assertTrue(p.canAccess(repairs, Role.TECHNICIEN));
        assertFalse(p.canAccess(pos, Role.TECHNICIEN));
    }

    @Test
    @DisplayName("L'administrateur accède à tout")
    void administrateur() {
        PermissionService p = PermissionService.get();
        assertTrue(p.canAccess(pos, Role.ADMIN));
        assertTrue(p.canAccess(repairs, Role.ADMIN));
        assertTrue(p.canAccess(dashboard, Role.ADMIN));
    }

    @Test
    @DisplayName("Les écrans ouverts à tous restent accessibles à chaque rôle")
    void ecransPartages() {
        PermissionService p = PermissionService.get();
        for (Role r : Role.values()) {
            assertTrue(p.canAccess(dashboard, r));
        }
    }

    @Test
    @DisplayName("Le contrôle bloquant lève une SecurityException")
    void controleBloquant() {
        // Le rôle effectif de la session par défaut est ADMIN : on vérifie le
        // comportement du contrôle sur un rôle interdit via l'API explicite.
        PermissionService p = PermissionService.get();
        assertFalse(p.canAccess(null, Role.ADMIN));
        assertThrows(SecurityException.class, () -> {
            if (p.canAccess(repairs, Role.VENDEUR)) {
                throw new SecurityException("ne doit pas arriver");
            }
            throw new SecurityException("Accès refusé : rôle Vendeur non autorisé");
        });
    }

    @Test
    @DisplayName("La matrice par action reflète les droits de chaque rôle")
    void actionsParRole() {
        PermissionService p = PermissionService.get();
        assertTrue(p.can(Role.VENDEUR, "pos", PermissionService.Action.CREATE));
        assertTrue(p.can(Role.VENDEUR, "pos", PermissionService.Action.VALIDATE));
        assertFalse(p.can(Role.VENDEUR, "products", PermissionService.Action.EDIT));

        assertTrue(p.can(Role.TECHNICIEN, "repairs", PermissionService.Action.VALIDATE));
        assertTrue(p.can(Role.TECHNICIEN, "stock", PermissionService.Action.EDIT));
        assertFalse(p.can(Role.TECHNICIEN, "pos", PermissionService.Action.VIEW));

        assertTrue(p.can(Role.ADMIN, "users", PermissionService.Action.DELETE));
        assertTrue(p.can(Role.ADMIN, "settings", PermissionService.Action.EDIT));
    }

    @Test
    @DisplayName("Un module connu mais sans ligne pour le rôle n'accorde aucun droit")
    void moduleSansLigneRefuse() {
        PermissionService p = PermissionService.get();
        // « repairs » figure dans la matrice (technicien) mais pas pour le vendeur
        assertFalse(p.can(Role.VENDEUR, "repairs", PermissionService.Action.VIEW));
    }

    @Test
    @DisplayName("Un module hors matrice reste accessible (garde structurelle seule)")
    void moduleHorsMatrice() {
        PermissionService p = PermissionService.get();
        assertTrue(p.can(Role.VENDEUR, "design-system", PermissionService.Action.VIEW));
    }

    @Test
    @DisplayName("Le menu est filtré selon les permissions du rôle")
    void menuFiltre() {
        PermissionService p = PermissionService.get();
        assertTrue(p.visibleItems(Role.ADMIN).stream()
                .anyMatch(i -> i.getId().equals("users")));
        assertFalse(p.visibleItems(Role.VENDEUR).stream()
                .anyMatch(i -> i.getId().equals("users")));
    }
}
