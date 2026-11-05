package com.techshop.service;

import com.techshop.model.NavItem;

import java.util.List;

/**
 * Registre central des écrans de l'application.
 *
 * <p>Le module Utilisateurs a été retiré : il n'existe plus de rôles ni de
 * matrice de permissions, tous les écrans sont accessibles aux comptes
 * authentifiés.</p>
 */
public final class MenuRegistry {

    /** Section « Gestion » regroupe les modules métier. */
    public static final String SEC_OVERVIEW = "EXPLORATION";
    public static final String SEC_SALES = "VENTES";
    public static final String SEC_CATALOG = "CATALOGUE";
    public static final String SEC_SERVICE = "SERVICE";
    public static final String SEC_FINANCE = "FINANCES";
    public static final String SEC_ADMIN = "ADMINISTRATION";

    private static final List<NavItem> ITEMS = List.of(
            NavItem.all("dashboard", "Tableau de bord", SEC_OVERVIEW, "home", "screens/dashboard"),

            NavItem.all("pos", "Point de vente", SEC_SALES, "shopping-cart", "screens/pos"),
            NavItem.all("sales", "Ventes", SEC_SALES, "shopping-bag", "screens/sales"),
            NavItem.all("customers", "Clients", SEC_SALES, "users", "screens/customers"),

            NavItem.all("products", "Produits", SEC_CATALOG, "box", "screens/products"),
            NavItem.all("stock", "Stock", SEC_CATALOG, "layers", "screens/stock"),

            NavItem.all("repairs", "SAV / Réparations", SEC_SERVICE, "tool", "screens/repairs"),

            NavItem.all("cash", "Caisse & Finances", SEC_FINANCE, "briefcase", "screens/cash"),
            NavItem.all("reports", "Rapports", SEC_FINANCE, "bar-chart-2", "screens/reports"),

            NavItem.all("settings", "Paramètres", SEC_ADMIN, "settings", "screens/settings")
    );

    private MenuRegistry() {
    }

    /** Tous les écrans (d'ordre d'affichage). */
    public static List<NavItem> all() {
        return ITEMS;
    }

    public static NavItem byId(String id) {
        return ITEMS.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }
}
