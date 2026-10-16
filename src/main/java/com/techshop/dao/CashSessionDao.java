package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.CashSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux sessions de caisse (cash_sessions, SPEC §4.10). La session ouverte
 * d'un utilisateur est unique ; la clôture fige les montants attendus, le
 * comptage et l'écart.
 */
public class CashSessionDao {

    private static final String SELECT = "SELECT cs.id, cs.number, cs.user_id, u.full_name AS username, "
            + "cs.opened_at, cs.closed_at, cs.opening_float, cs.expected_cash, cs.counted_cash, "
            + "cs.difference, cs.expected_card, cs.expected_transfer, cs.total_sales, cs.status, "
            + "cs.note FROM cash_sessions cs LEFT JOIN users u ON u.id = cs.user_id ";

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(MAX(CAST(SUBSTRING(number, 3) AS UNSIGNED)), 0) + 1 "
                             + "FROM cash_sessions WHERE number LIKE 'C-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(CashSession s) throws SQLException {
        if (s.getNumber() == null || s.getNumber().isBlank()) {
            s.setNumber("C-" + String.format("%06d", nextNumber()));
        }
        String sql = "INSERT INTO cash_sessions (number, user_id, opened_at, opening_float, status, note) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getNumber());
            ps.setInt(2, s.getUserId());
            ps.setTimestamp(3, Timestamp.valueOf(
                    s.getOpenedAt() == null ? java.time.LocalDateTime.now() : s.getOpenedAt()));
            ps.setBigDecimal(4, s.getOpeningFloat());
            ps.setString(5, s.getStatus() == null ? CashSession.OUVERTE : s.getStatus());
            ps.setString(6, s.getNote());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Ouverture de caisse échouée");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                s.setId(id);
                return id;
            }
        }
    }

    public Optional<CashSession> findOpenByUser(int userId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT + "WHERE cs.user_id = ? AND cs.status = 'OUVERTE' "
                             + "ORDER BY cs.opened_at DESC LIMIT 1")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<CashSession> findAnyOpen() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT + "WHERE cs.status = 'OUVERTE' ORDER BY cs.opened_at DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<CashSession> byId(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE cs.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Clôture : fige attendus, comptage, écart et total des ventes. */
    public void close(int id, BigDecimal expectedCash, BigDecimal countedCash,
                      BigDecimal difference, BigDecimal expectedCard, BigDecimal expectedTransfer,
                      BigDecimal totalSales, String note) throws SQLException {
        String sql = "UPDATE cash_sessions SET closed_at = ?, expected_cash = ?, counted_cash = ?, "
                + "difference = ?, expected_card = ?, expected_transfer = ?, total_sales = ?, "
                + "status = 'FERMEE', note = ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setBigDecimal(2, expectedCash);
            ps.setBigDecimal(3, countedCash);
            ps.setBigDecimal(4, difference);
            ps.setBigDecimal(5, expectedCard);
            ps.setBigDecimal(6, expectedTransfer);
            ps.setBigDecimal(7, totalSales);
            ps.setString(8, note);
            ps.setInt(9, id);
            ps.executeUpdate();
        }
    }

    public List<CashSession> recent(int limit) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT + "ORDER BY cs.opened_at DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<CashSession> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    static CashSession map(ResultSet rs) throws SQLException {
        CashSession s = new CashSession();
        s.setId(rs.getInt("id"));
        s.setNumber(rs.getString("number"));
        s.setUserId(rs.getInt("user_id"));
        s.setUsername(rs.getString("username"));
        Timestamp o = rs.getTimestamp("opened_at");
        s.setOpenedAt(o == null ? null : o.toLocalDateTime());
        Timestamp cl = rs.getTimestamp("closed_at");
        s.setClosedAt(cl == null ? null : cl.toLocalDateTime());
        s.setOpeningFloat(rs.getBigDecimal("opening_float"));
        s.setExpectedCash(rs.getBigDecimal("expected_cash"));
        s.setCountedCash(rs.getBigDecimal("counted_cash"));
        s.setDifference(rs.getBigDecimal("difference"));
        s.setExpectedCard(rs.getBigDecimal("expected_card"));
        s.setExpectedTransfer(rs.getBigDecimal("expected_transfer"));
        s.setTotalSales(rs.getBigDecimal("total_sales"));
        s.setStatus(rs.getString("status"));
        s.setNote(rs.getString("note"));
        return s;
    }
}
