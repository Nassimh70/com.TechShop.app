package com.techshop.config;

/**
 * Produit et options d'édition activées pour l'installation.
 *
 * <p>Le mode mono-rôle conserve les anciens types techniques dans le modèle
 * pour préserver la compatibilité des données et des tests, mais l'interface
 * et les services exposent un seul niveau d'accès.</p>
 */
public final class AppEdition {

    public static final boolean SINGLE_ROLE = true;
    public static final String SINGLE_ROLE_CODE = "ADMIN";

    private AppEdition() {
    }

    public static boolean isSingleRole() {
        return SINGLE_ROLE;
    }
}
