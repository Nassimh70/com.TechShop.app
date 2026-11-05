package com.techshop.service;

import com.techshop.model.NavItem;
import com.techshop.model.Role;

import java.util.List;

/**
 * Contrôles d'accès applicatifs.
 *
 * <p>Le module Utilisateurs, les rôles et la matrice de permissions ont été
 * retirés. Tous les comptes authentifiés disposent donc des mêmes droits.
 * Les méthodes sont conservées afin que les services et l'interface continuent
 * d'appeler un point unique, sans dépendre d'une table « permissions ».</p>
 */
public final class PermissionService {

    /** Actions historiquement contrôlées (conservées pour les appels existants). */
    public enum Action {
        VIEW, CREATE, EDIT, DELETE, VALIDATE
    }

    private static final PermissionService INSTANCE = new PermissionService();

    private PermissionService() {
    }

    public static PermissionService get() {
        return INSTANCE;
    }

    // ----------------------------------------------------------- consultation

    /** Tous les droits sont accordés : il n'existe plus de rôles. */
    public boolean can(String module, Action action) {
        return true;
    }

    /** Tous les droits sont accordés : il n'existe plus de rôles. */
    public boolean can(Role role, String module, Action action) {
        return true;
    }

    public boolean canView(Role role, String module) {
        return true;
    }

    // ------------------------------------------------------------- contrôles

    /** Point d'ancrage historique : plus aucune restriction par rôle. */
    public void check(String module, Action action) {
        // Aucun rôle ni permission : rien à vérifier.
    }

    // ---------------------------------------------------------------- menus

    /** Tous les écrans sont accessibles aux comptes authentifiés. */
    public boolean canAccess(NavItem item) {
        return item != null;
    }

    /** Tous les écrans sont accessibles aux comptes authentifiés. */
    public boolean canAccess(NavItem item, Role role) {
        return item != null && role != null;
    }

    /** Écrans visibles : la totalité du menu. */
    public List<NavItem> visibleItems(Role role) {
        return MenuRegistry.all();
    }

    /** Contrôle d'accès applicatif sur un écran (toujours autorisé si défini). */
    public void check(NavItem item) {
        if (item == null) {
            throw new SecurityException("Écran inconnu.");
        }
    }
}
