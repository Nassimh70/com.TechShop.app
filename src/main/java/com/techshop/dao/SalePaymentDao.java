package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.SalePayment;

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

/** Accès aux règlements d'une vente (sale_payments). */
public class SalePaymentDao {

    public long insert(SalePayment p) throws SQLException {
        String sql = "INSERT INTO sale_payments (sale_id, payment_date, method, amount, "
                + "cash_session_id, note, user_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getSaleId());
            ps.setTimestamp(2, Timestamp.valueOf(
                    p.getPaymentDate() == null ? LocalDateTime.now() : p.getPaymentDate()));
            ps.setString(3, p.getMethod());
            ps.setBigDecimal(4, p.getAmount());
            ps.setObject(5, p.getCashSessionId());
            ps.setString(6, p.getNote());
            ps.setInt(7, p.getUserId() == null ? 1 : p.getUserId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Insertion du règlement échouée");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                p.setId((int) id);
                return id;
            }
        }
    }

    public List<SalePayment> listBySale(int saleId) throws SQLException {
        String sql = "SELECT id, sale_id, payment_date, method, amount, cash_session_id, note, "
                + "user_id FROM sale_payments WHERE sale_id = ? ORDER BY id";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SalePayment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Somme des règlements d'une session pour un mode donné (carte, virement...). */
    public BigDecimal sumBySessionAndMethod(int sessionId, String method) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM sale_payments "
                + "WHERE cash_session_id = ? AND method = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setString(2, method);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : null;
                return v == null ? BigDecimal.ZERO : v;
            }
        }
    }

    static SalePayment map(ResultSet rs) throws SQLException {
        SalePayment p = new SalePayment();
        p.setId(rs.getInt("id"));
        p.setSaleId(rs.getInt("sale_id"));
        Timestamp d = rs.getTimestamp("payment_date");
        p.setPaymentDate(d == null ? null : d.toLocalDateTime());
        p.setMethod(rs.getString("method"));
        p.setAmount(rs.getBigDecimal("amount"));
        int s = rs.getInt("cash_session_id");
        p.setCashSessionId(rs.wasNull() ? null : s);
        p.setNote(rs.getString("note"));
        p.setUserId(rs.getInt("user_id"));
        return p;
    }
}
