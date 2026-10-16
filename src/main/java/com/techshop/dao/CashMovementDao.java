package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.CashMovement;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux mouvements de caisse (cash_movements, SPEC §4.10). Chaque
 * encaissement ou sortie d'espèces alimente le solde théorique de la session.
 */
public class CashMovementDao {

    private static final String SELECT = "SELECT m.id, m.cash_session_id, m.type, m.amount, m.reason, "
            + "m.category_id, ec.name AS category_name, m.reference_type, m.reference_id, "
            + "m.user_id, u.full_name AS username, m.movement_date, m.note "
            + "FROM cash_movements m LEFT JOIN expense_categories ec ON ec.id = m.category_id "
            + "LEFT JOIN users u ON u.id = m.user_id ";

    public long insert(CashMovement m) throws SQLException {
        String sql = "INSERT INTO cash_movements (cash_session_id, type, amount, reason, category_id, "
                + "reference_type, reference_id, user_id, movement_date, note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, m.getCashSessionId());
            ps.setString(2, m.getType());
            ps.setBigDecimal(3, m.getAmount());
            ps.setString(4, m.getReason());
            ps.setObject(5, m.getCategoryId());
            ps.setString(6, m.getReferenceType());
            ps.setObject(7, m.getReferenceId());
            ps.setInt(8, m.getUserId());
            ps.setTimestamp(9, Timestamp.valueOf(
                    m.getMovementDate() == null ? LocalDateTime.now() : m.getMovementDate()));
            ps.setString(10, m.getNote());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Insertion du mouvement de caisse échouée");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                m.setId(id);
                return id;
            }
        }
    }

    public List<CashMovement> listBySession(int sessionId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT + "WHERE m.cash_session_id = ? ORDER BY m.movement_date DESC, m.id DESC")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CashMovement> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Somme des mouvements d'un type pour une session (espèces). */
    public BigDecimal sumByType(int sessionId, String type) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM cash_movements "
                             + "WHERE cash_session_id = ? AND type = ?")) {
            ps.setInt(1, sessionId);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : null;
                return v == null ? BigDecimal.ZERO : v;
            }
        }
    }

    public long countBySession(int sessionId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM cash_movements WHERE cash_session_id = ?")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    static CashMovement map(ResultSet rs) throws SQLException {
        CashMovement m = new CashMovement();
        m.setId(rs.getLong("id"));
        m.setCashSessionId(rs.getInt("cash_session_id"));
        m.setType(rs.getString("type"));
        m.setAmount(rs.getBigDecimal("amount"));
        m.setReason(rs.getString("reason"));
        int cat = rs.getInt("category_id");
        m.setCategoryId(rs.wasNull() ? null : cat);
        m.setCategoryName(rs.getString("category_name"));
        m.setReferenceType(rs.getString("reference_type"));
        int ref = rs.getInt("reference_id");
        m.setReferenceId(rs.wasNull() ? null : ref);
        m.setUserId(rs.getInt("user_id"));
        m.setUsername(rs.getString("username"));
        Timestamp d = rs.getTimestamp("movement_date");
        m.setMovementDate(d == null ? null : d.toLocalDateTime());
        m.setNote(rs.getString("note"));
        return m;
    }
}
