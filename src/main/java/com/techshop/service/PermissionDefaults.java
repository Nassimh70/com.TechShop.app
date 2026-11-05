package com.techshop.service;

/**
 * Identifiants des modules utilisés par les contrôles d'accès applicatifs.
 *
 * <p>La matrice de permissions et les rôles ont été supprimés : les contrôles
 * sont conservés comme points d'ancrage dans les services, mais tous les
 * comptes authentifiés disposent des mêmes droits.</p>
 */
public final class PermissionDefaults {

    /** Modules de l'application (identiques aux identifiants de navigation). */
    public static final String M_DASHBOARD = "dashboard";
    public static final String M_POS = "pos";
    public static final String M_SALES = "sales";
    public static final String M_CUSTOMERS = "customers";
    public static final String M_PRODUCTS = "products";
    public static final String M_STOCK = "stock";
    public static final String M_REPAIRS = "repairs";
    public static final String M_CASH = "cash";
    public static final String M_REPORTS = "reports";
    public static final String M_SETTINGS = "settings";

    private PermissionDefaults() {
    }
}
