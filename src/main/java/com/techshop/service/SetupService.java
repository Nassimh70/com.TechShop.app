package com.techshop.service;

import com.techshop.config.DatabaseManager;
import com.techshop.config.Tx;
import com.techshop.dao.UserDao;
import com.techshop.util.Passwords;
import com.techshop.util.ValidationException;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Initialisation locale d'une installation et récupération du compte gérant.
 */
public final class SetupService {

    private static final SetupService INSTANCE = new SetupService(new UserDao());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserDao users;

    public record SetupData(String storeName, String address, String phone, String email,
                            String taxId, String currency, String vat, String backupDir,
                            String ownerName, String username, String password,
                            String confirmation) {
    }

    private SetupService(UserDao users) {
        this.users = users;
    }

    public static SetupService get() {
        return INSTANCE;
    }

    public boolean isSetupRequired() {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT valeur FROM settings WHERE cle = 'setup_completed'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return !"true".equalsIgnoreCase(rs.getString(1));
            }
            try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet usersCount = count.executeQuery()) {
                usersCount.next();
                if (usersCount.getInt(1) == 0) {
                    return true;
                }
            }
            try (PreparedStatement legacy = c.prepareStatement(
                    "SELECT COUNT(*) FROM users WHERE username = 'admin' AND must_change_password = 1");
                 ResultSet legacyUser = legacy.executeQuery()) {
                legacyUser.next();
                return legacyUser.getInt(1) == 1;
            }
        } catch (SQLException e) {
            throw new ValidationException("Impossible de vérifier l'état de configuration.");
        }
    }

    public String initialize(SetupData data) {
        validate(data);
        String recoveryKey = generateKey();
        try {
            return Tx.run(() -> {
                replaceLegacyAdminIfNeeded();
                if (countUsers() > 0) {
                    throw new ValidationException(
                            "Cette installation possède déjà un compte. Configuration refusée.");
                }
                int ownerId = users.insert(data.username().trim(), Passwords.hash(data.password()),
                        data.ownerName().trim(), null, blank(data.email()), true, false);
                set("store.name", data.storeName().trim());
                set("store.address", blank(data.address()));
                set("store.phone", blank(data.phone()));
                set("store.email", blank(data.email()));
                set("store.tax_id", blank(data.taxId()));
                set("store.currency", data.currency().trim());
                set("sales.vat_default", data.vat().trim());
                set("backup.dir", blank(data.backupDir()));
                set("recovery.user_id", String.valueOf(ownerId));
                set("recovery.hash", Passwords.hash(recoveryKey));
                set("setup_completed", "true");
                return recoveryKey;
            });
        } catch (SQLException e) {
            throw new ValidationException("Initialisation impossible. Aucune modification n'a été conservée.");
        }
    }

    public String resetPasswordWithRecoveryKey(String recoveryKey, String newPassword,
                                                String confirmation) {
        if (recoveryKey == null || recoveryKey.isBlank()
                || !recoveryKey.equals(recoveryKey.trim())) {
            throw new ValidationException("Clé de récupération invalide.");
        }
        validatePassword(newPassword, confirmation);
        try {
            return Tx.run(() -> {
                String hash = value("recovery.hash");
                int userId = Integer.parseInt(value("recovery.user_id"));
                String lockedUntil = valueOr("recovery.locked_until", "");
                if (!lockedUntil.isBlank()
                        && LocalDateTime.parse(lockedUntil).isAfter(LocalDateTime.now())) {
                    throw new ValidationException("Récupération temporairement bloquée. Réessayez plus tard.");
                }
                if (!Passwords.verify(recoveryKey, hash)) {
                    int attempts = Integer.parseInt(valueOr("recovery.failed_attempts", "0")) + 1;
                    set("recovery.failed_attempts", String.valueOf(attempts));
                    if (attempts >= LoginPolicy.MAX_ECHECS) {
                        set("recovery.locked_until",
                                LocalDateTime.now().plus(LoginPolicy.DUREE_BLOCAGE).toString());
                    }
                    throw new ValidationException("Clé de récupération invalide.");
                }
                users.updatePassword(userId, Passwords.hash(newPassword), false);
                String next = generateKey();
                set("recovery.hash", Passwords.hash(next));
                set("recovery.failed_attempts", "0");
                set("recovery.locked_until", "");
                ActivityLogService.get().log(null, "RECUPERATION_COMPTE", "users", userId,
                        "Mot de passe réinitialisé avec la clé de récupération");
                return next;
            });
        } catch (SQLException | NumberFormatException e) {
            throw new ValidationException("Récupération du compte impossible.");
        }
    }

    private void replaceLegacyAdminIfNeeded() throws SQLException {
        if (countUsers() == 1) {
            try (Connection c = DatabaseManager.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM users WHERE username = 'admin' AND must_change_password = 1")) {
                ps.executeUpdate();
            }
        }
    }

    private int countUsers() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void set(String key, String value) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO settings (cle, valeur) VALUES (?, ?) "
                             + "ON CONFLICT(cle) DO UPDATE SET valeur = excluded.valeur")) {
            ps.setString(1, key);
            ps.setString(2, value == null ? "" : value);
            ps.executeUpdate();
        }
    }

    private String value(String key) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT valeur FROM settings WHERE cle = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Paramètre manquant : " + key);
                }
                return rs.getString(1);
            }
        }
    }

    private String valueOr(String key, String fallback) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT valeur FROM settings WHERE cle = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : fallback;
            }
        }
    }

    private static void validate(SetupData data) {
        if (data == null || data.storeName() == null || data.storeName().isBlank()) {
            throw new ValidationException("Le nom du magasin est obligatoire.");
        }
        if (data.ownerName() == null || data.ownerName().trim().length() < 3) {
            throw new ValidationException("Le nom complet du gérant est obligatoire.");
        }
        if (data.username() == null || !data.username().matches("[A-Za-z0-9._-]{3,60}")) {
            throw new ValidationException("L'identifiant doit comporter 3 à 60 caractères valides.");
        }
        validatePassword(data.password(), data.confirmation());
        if (data.currency() == null || data.currency().isBlank()) {
            throw new ValidationException("La devise est obligatoire.");
        }
    }

    private static void validatePassword(String password, String confirmation) {
        if (password == null || !password.equals(confirmation)) {
            throw new ValidationException("Les mots de passe ne correspondent pas.");
        }
        String error = Passwords.validate(password);
        if (error != null) {
            throw new ValidationException(error);
        }
    }

    private static String generateKey() {
        StringBuilder key = new StringBuilder(29);
        for (int i = 0; i < 24; i++) {
            if (i > 0 && i % 6 == 0) {
                key.append('-');
            }
            key.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return key.toString();
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
