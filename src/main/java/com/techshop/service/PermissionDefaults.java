package com.techshop.service;

import com.techshop.model.Permission;
import com.techshop.model.Role;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matrice de permissions par défaut (SPEC §3), miroir exact du script
 * V002__initial_data.sql. Elle sert de repli lorsque la base n'est pas encore
 * joignable (l'application démarre en mode dégradé) et garantit que le filtrage
 * du menu reste cohérent même sans table « permissions ».
 */
public final class PermissionDefaults {

    /** Modules de la matrice (identiques aux identifiants de navigation). */
    public static final String M_DASHBOARD = "dashboard";
    public static final String M_POS = "pos";
    public static final String M_SALES = "sales";
    public static final String M_CUSTOMERS = "customers";
    public static final String M_PRODUCTS = "products";
    public static final String M_STOCK = "stock";
    public static final String M_PURCHASES = "purchases";
    public static final String M_REPAIRS = "repairs";
    public static final String M_CASH = "cash";
    public static final String M_REPORTS = "reports";
    public static final String M_USERS = "users";
    public static final String M_SETTINGS = "settings";

    /** Ordre d'affichage des modules dans la matrice. */
    public static final java.util.List<String> MODULES = java.util.List.of(
            M_DASHBOARD, M_POS, M_SALES, M_CUSTOMERS, M_PRODUCTS, M_STOCK,
            M_PURCHASES, M_REPAIRS, M_CASH, M_REPORTS, M_USERS, M_SETTINGS);

    private PermissionDefaults() {
    }

    /** Matrice complète pour la couche service (repli hors base). */
    public static Map<Role, Map<String, Permission>> matrice() {
        Map<Role, Map<String, Permission>> m = new java.util.EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            m.put(r, pourRole(r));
        }
        return m;
    }

    /** Droits par défaut d'un rôle, par module. */
    public static Map<String, Permission> pourRole(Role role) {
        Map<String, Permission> perms = new LinkedHashMap<>();
        switch (role) {
            case ADMIN -> {
                for (String module : MODULES) {
                    perms.put(module, Permission.all(module));
                }
            }
            case VENDEUR -> {
                perms.put(M_DASHBOARD, new Permission(M_DASHBOARD, true, false, false, false, false));
                perms.put(M_POS, new Permission(M_POS, true, true, true, false, true));
                perms.put(M_SALES, new Permission(M_SALES, true, true, true, false, true));
                perms.put(M_CUSTOMERS, new Permission(M_CUSTOMERS, true, true, true, false, false));
                perms.put(M_PRODUCTS, new Permission(M_PRODUCTS, true, false, false, false, false));
                perms.put(M_STOCK, new Permission(M_STOCK, true, false, false, false, false));
                perms.put(M_CASH, new Permission(M_CASH, true, true, true, false, true));
            }
            case TECHNICIEN -> {
                perms.put(M_DASHBOARD, new Permission(M_DASHBOARD, true, false, false, false, false));
                perms.put(M_REPAIRS, new Permission(M_REPAIRS, true, true, true, false, true));
                perms.put(M_PRODUCTS, new Permission(M_PRODUCTS, true, false, false, false, false));
                perms.put(M_STOCK, new Permission(M_STOCK, true, false, true, false, false));
            }
        }
        return perms;
    }
}
