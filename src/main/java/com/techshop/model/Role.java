package com.techshop.model;

/**
 * Profil d'accès unique de l'application.
 *
 * <p>Le module Utilisateurs, les rôles et la matrice de permissions ont été
 * retirés. Tous les comptes disposent donc du même niveau d'accès. La constante
 * est conservée pour l'affichage de l'en-tête et la compatibilité des modèles.</p>
 */
public enum Role {

    ADMIN("Gérant");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
