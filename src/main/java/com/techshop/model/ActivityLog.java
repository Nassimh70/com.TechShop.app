package com.techshop.model;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'activité (SPEC §5, §6) : qui a fait quoi, sur quoi,
 * quand. Sert au module Utilisateurs (journal filtrable) et aux audits.
 */
public record ActivityLog(long id, Integer userId, String username, String action,
                          String entity, Integer entityId, String details, String ip,
                          LocalDateTime createdAt) {

    /** Libellé d'action prêt à l'affichage (codes -> libellés français). */
    public String actionLabel() {
        if (action == null) {
            return "—";
        }
        return switch (action) {
            case ActivityActions.CONNEXION -> "Connexion";
            case ActivityActions.ECHEC_CONNEXION -> "Échec de connexion";
            case ActivityActions.DECONNEXION -> "Déconnexion";
            case ActivityActions.CHANGEMENT_MDP -> "Changement de mot de passe";
            case ActivityActions.REINIT_MDP -> "Réinitialisation de mot de passe";
            case ActivityActions.CREATION -> "Création";
            case ActivityActions.MODIFICATION -> "Modification";
            case ActivityActions.ACTIVATION -> "Activation";
            case ActivityActions.DESACTIVATION -> "Désactivation";
            case ActivityActions.PERMISSIONS -> "Permissions modifiées";
            case ActivityActions.STOCK_MOUVEMENT -> "Mouvement de stock";
            case ActivityActions.STOCK_AJUSTEMENT -> "Ajustement de stock";
            case ActivityActions.IMPORT_CSV -> "Import CSV";
            case ActivityActions.CHANGEMENT_PRIX -> "Changement de prix";
            case ActivityActions.ETIQUETTES -> "Étiquettes imprimées";
            default -> action;
        };
    }
}
