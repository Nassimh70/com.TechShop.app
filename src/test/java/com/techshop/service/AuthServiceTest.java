package com.techshop.service;

import com.techshop.dao.UserDao;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.util.Passwords;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de l'authentification (SPEC §4.1) sans base de données : le DAO, le
 * journal d'audit et l'horloge sont injectés.
 */
class AuthServiceTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 1, 15, 9, 30);
    private static final String MOT_DE_PASSE = "admin123";

    /** DAO en mémoire : rejoue les mutations sur une instance immuable de User. */
    private static final class FakeUserDao extends UserDao {

        User user;
        int reussites;
        int echecs;
        int derniereTentative;
        LocalDateTime derniereBlocage;

        FakeUserDao(User user) {
            this.user = user;
        }

        @Override
        public Optional<User> findByUsername(String username) throws SQLException {
            return user != null && user.getUsername().equals(username)
                    ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<User> findById(int id) throws SQLException {
            return user != null && user.getId() == id ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void recordLoginSuccess(int id) {
            reussites++;
            user = new User(user.getId(), user.getUsername(), user.getPasswordHash(),
                    user.getFullName(), user.getPhone(), user.getEmail(), user.getRole(),
                    user.isActive(), user.isMustChangePassword(), 0, null, MAINTENANT);
        }

        @Override
        public void recordLoginFailure(int id, int failedAttempts, LocalDateTime lockedUntil) {
            echecs++;
            derniereTentative = failedAttempts;
            derniereBlocage = lockedUntil;
            user = new User(user.getId(), user.getUsername(), user.getPasswordHash(),
                    user.getFullName(), user.getPhone(), user.getEmail(), user.getRole(),
                    user.isActive(), user.isMustChangePassword(), failedAttempts, lockedUntil,
                    user.getLastLogin());
        }

        @Override
        public void updatePassword(int id, String passwordHash, boolean mustChange) {
            user = new User(user.getId(), user.getUsername(), passwordHash, user.getFullName(),
                    user.getPhone(), user.getEmail(), user.getRole(), user.isActive(),
            mustChange, 0, null, user.getLastLogin());
        }
    }

    private FakeUserDao dao;
    private final List<String> actions = new ArrayList<>();
    private AuthService auth;

    private AuthService serviceAvec(User u, Supplier<LocalDateTime> horloge) {
        dao = new FakeUserDao(u);
        ActivityAudit audit = (userId, action, entity, entityId, details) -> actions.add(action);
        auth = new AuthService(dao, audit, horloge);
        return auth;
    }

    private static User utilisateur(String hash, boolean actif, boolean doitChanger) {
        return new User(1, "admin", hash, "Administrateur", null, null, Role.ADMIN,
                actif, doitChanger, 0, null, null);
    }

    @AfterEach
    void nettoyer() {
        SessionContext.get().setUser(null);
    }

    @Test
    @DisplayName("Une connexion valide ouvre la session et journalise l'action")
    void connexionReussie() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, false), () -> MAINTENANT);
        AuthService.Result r = auth.login("admin", MOT_DE_PASSE);

        assertEquals(AuthService.Status.SUCCESS, r.status());
        assertTrue(r.ok());
        assertNotNull(SessionContext.get().getUser());
        assertEquals(1, dao.reussites);
        assertTrue(actions.contains("CONNEXION"));
    }

    @Test
    @DisplayName("L'admin initial doit changer son mot de passe")
    void changementObligatoire() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, true), () -> MAINTENANT);
        AuthService.Result r = auth.login("admin", MOT_DE_PASSE);

        assertEquals(AuthService.Status.MUST_CHANGE_PASSWORD, r.status());
        assertTrue(r.ok());
    }

    @Test
    @DisplayName("Mauvais mot de passe : compteur d'échecs incrémenté")
    void mauvaisMotDePasse() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, false), () -> MAINTENANT);
        AuthService.Result r = auth.login("admin", "mauvais");

        assertEquals(AuthService.Status.INVALID_CREDENTIALS, r.status());
        assertEquals(4, r.tentativesRestantes());
        assertEquals(1, dao.derniereTentative);
        assertNull(SessionContext.get().getUser());
        assertTrue(actions.contains("ECHEC_CONNEXION"));
    }

    @Test
    @DisplayName("Cinq échecs bloquent temporairement le compte")
    void blocageApresCinqEchecs() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, false), () -> MAINTENANT);
        AuthService.Result r = null;
        for (int i = 0; i < 5; i++) {
            r = auth.login("admin", "mauvais");
        }
        assertEquals(AuthService.Status.LOCKED, r.status());
        assertTrue(r.minutesBlocage() > 0);
        assertEquals(MAINTENANT.plusMinutes(15), dao.derniereBlocage);

        // Tant que le blocage court, même le bon mot de passe est refusé
        AuthService.Result bloque = auth.login("admin", MOT_DE_PASSE);
        assertEquals(AuthService.Status.LOCKED, bloque.status());
    }

    @Test
    @DisplayName("Un compte désactivé ne peut pas se connecter")
    void compteDesactive() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), false, false), () -> MAINTENANT);
        AuthService.Result r = auth.login("admin", MOT_DE_PASSE);

        assertEquals(AuthService.Status.DISABLED, r.status());
        assertNull(SessionContext.get().getUser());
    }

    @Test
    @DisplayName("Identifiant inconnu ou champs vides : refus sans révéler le motif")
    void identifiantsInvalides() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, false), () -> MAINTENANT);
        assertEquals(AuthService.Status.INVALID_CREDENTIALS, auth.login("inconnu", "x").status());
        assertEquals(AuthService.Status.INVALID_CREDENTIALS, auth.login("", "").status());
        assertTrue(actions.contains("ECHEC_CONNEXION"));
    }

    @Test
    @DisplayName("Le changement de mot de passe met à jour l'empreinte et la session")
    void changementPropre() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, true), () -> MAINTENANT);
        auth.login("admin", MOT_DE_PASSE);
        auth.changeOwnPassword(1, MOT_DE_PASSE, "nouveau123", "nouveau123");

        assertTrue(Passwords.verify("nouveau123", dao.user.getPasswordHash()));
        assertTrue(!dao.user.isMustChangePassword());
        assertTrue(!SessionContext.get().getUser().isMustChangePassword());
        assertTrue(actions.contains("CHANGEMENT_MDP"));
    }

    @Test
    @DisplayName("Le changement de mot de passe valide l'ancien, la confirmation et la robustesse")
    void changementInvalide() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, true), () -> MAINTENANT);
        auth.login("admin", MOT_DE_PASSE);

        assertThrows(ValidationException.class, () ->
                auth.changeOwnPassword(1, MOT_DE_PASSE, "nouveau123", "different123"));
        assertThrows(ValidationException.class, () ->
                auth.changeOwnPassword(1, "mauvais", "nouveau123", "nouveau123"));
        assertThrows(ValidationException.class, () ->
                auth.changeOwnPassword(1, MOT_DE_PASSE, "court", "court"));
        assertThrows(ValidationException.class, () ->
                auth.changeOwnPassword(1, MOT_DE_PASSE, MOT_DE_PASSE, MOT_DE_PASSE));
    }

    @Test
    @DisplayName("La déconnexion vide la session et journalise")
    void deconnexion() {
        serviceAvec(utilisateur(Passwords.hash(MOT_DE_PASSE), true, false), () -> MAINTENANT);
        auth.login("admin", MOT_DE_PASSE);
        auth.logout();

        assertNull(SessionContext.get().getUser());
        assertTrue(actions.contains("DECONNEXION"));
    }
}
