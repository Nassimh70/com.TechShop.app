package com.techshop.model;

/**
 * Autorisations d'un rôle sur un module (SPEC §3).
 * Matrice persistée dans la table « permissions » : consulter, créer, modifier,
 * supprimer, valider.
 */
public record Permission(String module, boolean canView, boolean canCreate,
                         boolean canEdit, boolean canDelete, boolean canValidate) {

    /** Aucun droit. */
    public static Permission none(String module) {
        return new Permission(module, false, false, false, false, false);
    }

    /** Tous les droits. */
    public static Permission all(String module) {
        return new Permission(module, true, true, true, true, true);
    }
}
