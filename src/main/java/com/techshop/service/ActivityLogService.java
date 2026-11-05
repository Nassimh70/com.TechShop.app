package com.techshop.service;

import com.techshop.config.DatabaseManager;
import com.techshop.dao.ActivityLogDao;
import com.techshop.model.ActivityActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Journal d'activité (SPEC §5, §6) : trace les actions sensibles (connexions,
 * créations, modifications, réinitialisations...). L'écriture ne fait jamais
 * échouer l'action métier : toute erreur est journalisée localement par Logback.
 */
public final class ActivityLogService implements ActivityAudit {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private static final ActivityLogService INSTANCE =
            new ActivityLogService(new ActivityLogDao(), () -> DatabaseManager.isReady());

    private final ActivityLogDao dao;
    private final Supplier<Boolean> basePrete;

    public ActivityLogService(ActivityLogDao dao, Supplier<Boolean> basePrete) {
        this.dao = dao;
        this.basePrete = basePrete;
    }

    public static ActivityLogService get() {
        return INSTANCE;
    }

    /** Consigne l'action de l'utilisateur en session (le cas échéant). */
    public void log(String action, String entity, Integer entityId, String details) {
        var utilisateur = SessionContext.get().getUser();
        log(utilisateur == null ? null : utilisateur.getId(), action, entity, entityId, details);
    }

    @Override
    public void log(Integer userId, String action, String entity, Integer entityId, String details) {
        if (!Boolean.TRUE.equals(basePrete.get())) {
            log.debug("Journal ignoré (base indisponible) : {} {}", action, details);
            return;
        }
        try {
            dao.insert(userId, action, entity, entityId, details, "local");
        } catch (SQLException e) {
            log.warn("Écriture du journal d'activité impossible : {}", e.getMessage());
        }
    }

    /** Raccourci : consigne une connexion réussie pour l'utilisateur indiqué. */
    public void connexion(int userId, String username) {
        log(userId, ActivityActions.CONNEXION, "users", userId, "Connexion de « " + username + " »");
    }
}
