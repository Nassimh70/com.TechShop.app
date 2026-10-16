package com.techshop.model;

/** Rôles utilisateurs (SPEC §3). Les permissions sont vérifiées dans les services. */
public enum Role {

    ADMIN("Administrateur"),
    VENDEUR("Vendeur"),
    TECHNICIEN("Technicien");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
