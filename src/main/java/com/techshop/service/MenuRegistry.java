package com.techshop.service;

import com.techshop.model.NavItem;
import com.techshop.model.Role;

import java.util.List;

/**
 * Registre central des écrans et de leurs rôles autorisés (SPEC §3).
 * À terme, les permissions sont lues depuis la table « permissions » ;
 * la matrice ci-dessous est le socle vérifié dans les services.
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

            new NavItem("pos", "Point de vente", SEC_SALES, "shopping-cart", "screens/pos",
                    null, java.util.EnumSet.of(Role.ADMIN, Role.VENDEUR)),
            new NavItem("sales", "Ventes", SEC_SALES, "shopping-bag", "screens/sales",
                    null, java.util.EnumSet.of(Role.ADMIN, Role.VENDEUR)),
            new NavItem("customers", "Clients", SEC_SALES, "users", "screens/customers",
                    null, java.util.EnumSet.of(Role.ADMIN, Role.VENDEUR)),

            NavItem.all("products", "Produits", SEC_CATALOG, "box", "screens/products"),
            NavItem.all("stock", "Stock", SEC_CATALOG, "layers", "screens/stock"),

            new NavItem("repairs", "SAV / Réparations", SEC_SERVICE, "tool", "screens/repairs",
                    null, java.util.EnumSet.of(Role.ADMIN, Role.TECHNICIEN)),

            new NavItem("cash", "Caisse & Finances", SEC_FINANCE, "briefcase", "screens/cash",
                    null, java.util.EnumSet.of(Role.ADMIN, Role.VENDEUR)),

            new NavItem("reports", "Rapports", SEC_FINANCE, "bar-chart-2", "screens/reports",
                    null, java.util.EnumSet.of(Role.ADMIN)),

            new NavItem("users", "Utilisateurs", SEC_ADMIN, "user-check", "screens/users",
                    null, java.util.EnumSet.of(Role.ADMIN)),
            new NavItem("settings", "Paramètres", SEC_ADMIN, "settings", "screens/settings",
                    null, java.util.EnumSet.of(Role.ADMIN)),
            NavItem.all("design-system", "Design system", SEC_ADMIN, "grid", "screens/design-system")
    );

    private MenuRegistry() {
    }

    /** Tous les écrans (d'ordre d'affichage). */
    public static List<NavItem> all() {
        return ITEMS;
    }

    /** Écrans visibles pour un rôle donné. */
    public static List<NavItem> forRole(Role role) {
        return ITEMS.stream().filter(i -> i.allows(role)).toList();
    }

    public static NavItem byId(String id) {
        return ITEMS.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }
}
