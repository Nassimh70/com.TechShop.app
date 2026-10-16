package com.techshop.service;

import com.techshop.dao.UserDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.User;
import com.techshop.util.Passwords;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Authentification et gestion du mot de passe (SPEC §4.1).
 * Hachage BCrypt, blocage temporaire après 5 échecs, session en mémoire,
 * déconnexion, changement de mot de passe (obligatoire pour l'admin initial).
 * Testable : le DAO, l'audit et l'horloge sont injectables.
 */
public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public enum Status {
        /** Connexion réussie. */
        SUCCESS,
        /** Connexion réussie mais changement de mot de passe obligatoire. */
        MUST_CHANGE_PASSWORD,
        /** Identifiant ou mot de passe incorrect. */
        INVALID_CREDENTIALS,
        /** Compte temporairement bloqué. */
        LOCKED,
        /** Compte désactivé. */
        DISABLED,
        /** Base de données indisponible. */
        ERROR
    }

    /**
     * Résultat d'une tentative de connexion.
     *
     * @param status       issue
     * @param user         utilisateur authentifié (si succès)
     * @param message      message prêt à afficher
     * @param tentativesRestantes tentatives avant blocage (si identifiants erronés)
     * @param minutesBlocage minutes restantes de blocage (si bloqué)
     */
    public record Result(Status status, User user, String message,
                         int tentativesRestantes, long minutesBlocage) {

        public boolean ok() {
            return status == Status.SUCCESS || status == Status.MUST_CHANGE_PASSWORD;
        }
    }

    private static final AuthService INSTANCE = new AuthService(
            new UserDao(), ActivityLogService.get(), LocalDateTime::now);

    private final UserDao users;
    private final ActivityAudit audit;
    private final Supplier<LocalDateTime> horloge;

    public AuthService(UserDao users, ActivityAudit audit, Supplier<LocalDateTime> horloge) {
        this.users = users;
        this.audit = audit;
        this.horloge = horloge;
    }

    public static AuthService get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------ connexion

    /** Tentative de connexion par identifiant + mot de passe en clair. */
    public Result login(String username, String password) {
        String identifiant = username == null ? "" : username.trim();
        if (identifiant.isEmpty() || password == null || password.isEmpty()) {
            return new Result(Status.INVALID_CREDENTIALS, null,
                    "Veuillez saisir votre identifiant et votre mot de passe.", 0, 0);
        }
        try {
            Optional<User> trouve = users.findByUsername(identifiant);
            if (trouve.isEmpty()) {
                audit.log(null, ActivityActions.ECHEC_CONNEXION, "users", null,
                        "Identifiant inconnu : " + identifiant);
                return new Result(Status.INVALID_CREDENTIALS, null,
                        "Identifiant ou mot de passe incorrect.", 0, 0);
            }
            User u = trouve.get();
            if (!u.isActive()) {
                audit.log(u.getId(), ActivityActions.ECHEC_CONNEXION, "users", u.getId(),
                        "Compte désactivé : " + u.getUsername());
                return new Result(Status.DISABLED, null,
                        "Ce compte est désactivé. Contactez un administrateur.", 0, 0);
            }

            LocalDateTime maintenant = horloge.get();
            if (LoginPolicy.estBloque(u.getLockedUntil(), maintenant)) {
                long minutes = LoginPolicy.minutesRestantes(u.getLockedUntil(), maintenant);
                return new Result(Status.LOCKED, null,
                        "Compte temporairement bloqué. Réessayez dans " + minutes + " min.", 0, minutes);
            }

            if (!Passwords.verify(password, u.getPasswordHash())) {
                return echecConnexion(u, maintenant);
            }

            // Succès : compteurs remis à zéro, session ouverte
            users.recordLoginSuccess(u.getId());
            User connecte = users.findById(u.getId()).orElse(u);
            SessionContext.get().setUser(connecte);
            audit.log(connecte.getId(), ActivityActions.CONNEXION, "users", connecte.getId(),
                    "Connexion réussie : " + connecte.getUsername());
            if (connecte.isMustChangePassword()) {
                return new Result(Status.MUST_CHANGE_PASSWORD, connecte,
                        "Vous devez changer votre mot de passe avant de continuer.", 0, 0);
            }
            return new Result(Status.SUCCESS, connecte, "Connexion réussie.", 0, 0);
        } catch (SQLException e) {
            log.error("Erreur d'authentification", e);
            return new Result(Status.ERROR, null,
                    "Base de données indisponible. Vérifiez la connexion MySQL.", 0, 0);
        }
    }

    private Result echecConnexion(User u, LocalDateTime maintenant) throws SQLException {
        int echecs = u.getFailedAttempts() + 1;
        if (LoginPolicy.doitBloquer(echecs)) {
            LocalDateTime fin = LoginPolicy.finBlocage(maintenant);
            users.recordLoginFailure(u.getId(), echecs, fin);
            audit.log(u.getId(), ActivityActions.ECHEC_CONNEXION, "users", u.getId(),
                    "Blocage temporaire après " + echecs + " échecs");
            return new Result(Status.LOCKED, null,
                    "Trop de tentatives. Compte bloqué pendant "
                            + LoginPolicy.DUREE_BLOCAGE.toMinutes() + " min.", 0,
                    LoginPolicy.DUREE_BLOCAGE.toMinutes());
        }
        users.recordLoginFailure(u.getId(), echecs, null);
        audit.log(u.getId(), ActivityActions.ECHEC_CONNEXION, "users", u.getId(),
                "Mot de passe incorrect (" + echecs + "/" + LoginPolicy.MAX_ECHECS + ")");
        int restantes = LoginPolicy.tentativesRestantes(echecs);
        return new Result(Status.INVALID_CREDENTIALS, null,
                "Identifiant ou mot de passe incorrect. " + restantes
                        + " tentative(s) avant blocage.", restantes, 0);
    }

    // ------------------------------------------------------------ mot de passe

    /**
     * Change le mot de passe de l'utilisateur connecté après vérification de
     * l'ancien. Utilisé pour le premier changement obligatoire de l'admin.
     */
    public void changeOwnPassword(int userId, String ancien, String nouveau, String confirmation) {
        if (nouveau == null || !nouveau.equals(confirmation)) {
            throw new ValidationException("confirmation", "Les deux mots de passe ne correspondent pas.");
        }
        String erreur = Passwords.validate(nouveau);
        if (erreur != null) {
            throw new ValidationException("nouveau", erreur);
        }
        try {
            User u = users.findById(userId)
                    .orElseThrow(() -> new ValidationException("Utilisateur introuvable."));
            if (!Passwords.verify(ancien, u.getPasswordHash())) {
                throw new ValidationException("ancien", "L'ancien mot de passe est incorrect.");
            }
            if (Passwords.verify(nouveau, u.getPasswordHash())) {
                throw new ValidationException("nouveau",
                        "Le nouveau mot de passe doit être différent de l'ancien.");
            }
            users.updatePassword(userId, Passwords.hash(nouveau), false);
            audit.log(userId, ActivityActions.CHANGEMENT_MDP, "users", userId,
                    "Mot de passe modifié par l'utilisateur");
            // Rafraîchit la session (drapeau de changement levé)
            users.findById(userId).ifPresent(SessionContext.get()::setUser);
        } catch (SQLException e) {
            log.error("Changement de mot de passe impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Déconnexion : vide la session en mémoire et consigne l'action. */
    public void logout() {
        User u = SessionContext.get().getUser();
        if (u != null) {
            audit.log(u.getId(), ActivityActions.DECONNEXION, "users", u.getId(),
                    "Déconnexion de " + u.getUsername());
        }
        SessionContext.get().logout();
    }
}
