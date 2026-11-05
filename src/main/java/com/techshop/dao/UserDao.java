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
 * Accès aux comptes de connexion (SPEC §1 : JDBC pur, requêtes préparées).
 *
 * <p>Le module Utilisateurs et les rôles ont été retirés : la table
 * {@code users} ne contient plus de colonne {@code role_id}. Les comptes
 * restent nécessaires à l'authentification et à l'historique des opérations.</p>
 */
public class UserDao {

    /** Colonnes communes de sélection. */
    private static final String SELECT = "SELECT u.id, u.username, u.password_hash, u.full_name, "
            + "u.phone, u.email, u.active, u.must_change_password, u.failed_attempts, "
            + "u.locked_until, u.last_login "
            + "FROM users u ";

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

    /** Liste filtrée : recherche textuelle et statut actif. */
    public List<User> search(String recherche, Boolean actif) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (recherche != null && !recherche.isBlank()) {
            sql.append("AND (u.username LIKE ? OR u.full_name LIKE ? OR u.phone LIKE ?) ");
            String like = "%" + recherche.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
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

    /** Insère un compte et renvoie son identifiant généré. */
    public int insert(String username, String passwordHash, String fullName, String phone,
                      String email, boolean active, boolean mustChange) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, full_name, phone, email, "
                + "active, must_change_password) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, vide(phone));
            ps.setString(5, vide(email));
            ps.setInt(6, active ? 1 : 0);
            ps.setInt(7, mustChange ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
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
                Role.ADMIN,
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
