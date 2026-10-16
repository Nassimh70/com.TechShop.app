package com.techshop.service;

/**
 * Cible d'audit du journal d'activité. Permet d'injecter un puits silencieux
 * dans les services testables (authentification, utilisateurs) sans base.
 */
@FunctionalInterface
public interface ActivityAudit {

    /**
     * Consigne une action.
     *
     * @param userId   auteur (null si inconnu, ex. échec sur identifiant inexistant)
     * @param action   code d'action (voir {@link com.techshop.model.ActivityActions})
     * @param entity   table concernée (nullable)
     * @param entityId identifiant de l'enregistrement concerné (nullable)
     * @param details  détail lisible
     */
    void log(Integer userId, String action, String entity, Integer entityId, String details);

    /** Puits silencieux (tests, mode dégradé). */
    ActivityAudit SILENCIEUX = (userId, action, entity, entityId, details) -> {
    };
}
