package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Role;
import com.techshop.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux utilisateurs (SPEC §1 : JDBC pur, requêtes préparées).
 * Les méthodes sont surchargeables afin de permettre des tests sans base.
 */
public class UserDao {

    /** Colonnes communes de sélection (jointure rôle). */
    private static final String SELECT = "SELECT u.id, u.username, u.password_hash, u.full_name, "
            + "u.phone, u.email, u.active, u.must_change_password, u.failed_attempts, "
            + "u.locked_until, u.last_login, r.code AS role_code "
            + "FROM users u JOIN roles r ON r.id = u.role_id ";

    /** Recherche un utilisateur par identifiant de connexion. */
    public Optional<User> findByUsername(String username) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE u.username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findById(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE u.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Liste filtrée : recherche textuelle, rôle, statut actif. */
    public List<User> search(String recherche, Role role, Boolean actif) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (recherche != null && !recherche.isBlank()) {
            sql.append("AND (u.username LIKE ? OR u.full_name LIKE ? OR u.phone LIKE ?) ");
            String like = "%" + recherche.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (role != null) {
            sql.append("AND r.code = ? ");
            params.add(role.name());
        }
        if (actif != null) {
            sql.append("AND u.active = ? ");
            params.add(actif ? 1 : 0);
        }
        sql.append("ORDER BY u.full_name");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<User> liste = new ArrayList<>();
                while (rs.next()) {
                    liste.add(map(rs));
                }
                return liste;
            }
        }
    }

    /** Insère un utilisateur et renvoie son identifiant généré. */
    public int insert(String username, String passwordHash, String fullName, String phone,
                      String email, Role role, boolean active, boolean mustChange) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, full_name, phone, email, role_id, "
                + "active, must_change_password) VALUES (?, ?, ?, ?, ?, "
                + "(SELECT id FROM roles WHERE code = ?), ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, vide(phone));
            ps.setString(5, vide(email));
            ps.setString(6, role.name());
            ps.setInt(7, active ? 1 : 0);
            ps.setInt(8, mustChange ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /** Met à jour les informations d'un utilisateur (hors mot de passe). */
    public void update(int id, String fullName, String phone, String email,
                       Role role, boolean active) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, phone = ?, email = ?, "
                + "role_id = (SELECT id FROM roles WHERE code = ?), active = ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, vide(phone));
            ps.setString(3, vide(email));
            ps.setString(4, role.name());
            ps.setInt(5, active ? 1 : 0);
            ps.setInt(6, id);
            ps.executeUpdate();
        }
    }

    /** Active ou désactive un compte. */
    public void setActive(int id, boolean active) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Remplace l'empreinte du mot de passe et l'indicateur de changement. */
    public void updatePassword(int id, String passwordHash, boolean mustChange) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET password_hash = ?, must_change_password = ?, "
                             + "failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setInt(2, mustChange ? 1 : 0);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /** Consigne une connexion réussie : compteurs remis à zéro, date de connexion. */
    public void recordLoginSuccess(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET failed_attempts = 0, "
                     + "locked_until = NULL, last_login = CURRENT_TIMESTAMP WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Consigne un échec : compteur + fin de blocage éventuelle. */
    public void recordLoginFailure(int id, int failedAttempts, LocalDateTime lockedUntil)
            throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET failed_attempts = ?, "
                     + "locked_until = ? WHERE id = ?")) {
            ps.setInt(1, failedAttempts);
            ps.setTimestamp(2, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /** Nombre d'administrateurs actifs (protège le dernier admin). */
    public int countActiveAdmins() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE r.code = 'ADMIN' AND u.active = 1";
        try (Connection c = DatabaseManager.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Un identifiant de connexion est-il déjà pris (hors utilisateur exclu) ? */
    public boolean usernameExists(String username, int excludeId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE username = ? AND id <> ?")) {
            ps.setString(1, username);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    // ------------------------------------------------------------------ mapping

    private static User map(ResultSet rs) throws SQLException {
        Timestamp lock = rs.getTimestamp("locked_until");
        Timestamp last = rs.getTimestamp("last_login");
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role_code")),
                rs.getInt("active") == 1,
                rs.getInt("must_change_password") == 1,
                rs.getInt("failed_attempts"),
                lock == null ? null : lock.toLocalDateTime(),
                last == null ? null : last.toLocalDateTime());
    }

    private static String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
