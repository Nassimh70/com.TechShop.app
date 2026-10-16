package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.SaleReturn;

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
 * Accès aux retours / remboursements (sale_returns, sale_return_items).
 * Chaque retour porte son numéro (RT-) et ses lignes rendues.
 */
public class SaleReturnDao {

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(MAX(CAST(SUBSTRING(number, 4) AS UNSIGNED)), 0) + 1 "
                             + "FROM sale_returns WHERE number LIKE 'RT-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(SaleReturn r) throws SQLException {
        if (r.getNumber() == null || r.getNumber().isBlank()) {
            r.setNumber("RT-" + String.format("%06d", nextNumber()));
        }
        String sql = "INSERT INTO sale_returns (number, sale_id, return_date, total, reason, "
                + "refund_method, cash_session_id, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getNumber());
            ps.setInt(2, r.getSaleId());
            ps.setTimestamp(3, Timestamp.valueOf(
                    r.getReturnDate() == null ? LocalDateTime.now() : r.getReturnDate()));
            ps.setBigDecimal(4, r.getTotal());
            ps.setString(5, r.getReason());
            ps.setString(6, r.getRefundMethod());
            ps.setObject(7, r.getCashSessionId());
            ps.setInt(8, r.getUserId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Insertion du retour échouée");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                r.setId(id);
                return id;
            }
        }
    }

    public long insertItem(int returnId, int saleItemId, int quantity, BigDecimal unitPrice,
                           BigDecimal total, String productName) throws SQLException {
        String sql = "INSERT INTO sale_return_items (return_id, sale_item_id, quantity, unit_price, "
                + "total) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, returnId);
            ps.setInt(2, saleItemId);
            ps.setInt(3, quantity);
            ps.setBigDecimal(4, unitPrice);
            ps.setBigDecimal(5, total);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    /** Quantité déjà retournée pour une ligne de vente (tous retours confondus). */
    public int returnedQuantity(int saleItemId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(quantity), 0) FROM sale_return_items WHERE sale_item_id = ?")) {
            ps.setInt(1, saleItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<SaleReturn> listBySale(int saleId) throws SQLException {
        String sql = "SELECT r.id, r.number, r.sale_id, r.return_date, r.total, r.reason, "
                + "r.refund_method, r.cash_session_id, r.user_id, u.full_name AS username "
                + "FROM sale_returns r LEFT JOIN users u ON u.id = r.user_id "
                + "WHERE r.sale_id = ? ORDER BY r.id DESC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SaleReturn> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    public List<SaleReturn> recent(int limit) throws SQLException {
        String sql = "SELECT r.id, r.number, r.sale_id, r.return_date, r.total, r.reason, "
                + "r.refund_method, r.cash_session_id, r.user_id, u.full_name AS username "
                + "FROM sale_returns r LEFT JOIN users u ON u.id = r.user_id "
                + "ORDER BY r.return_date DESC, r.id DESC LIMIT ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<SaleReturn> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    static SaleReturn map(ResultSet rs) throws SQLException {
        SaleReturn r = new SaleReturn();
        r.setId(rs.getInt("id"));
        r.setNumber(rs.getString("number"));
        r.setSaleId(rs.getInt("sale_id"));
        Timestamp d = rs.getTimestamp("return_date");
        r.setReturnDate(d == null ? null : d.toLocalDateTime());
        r.setTotal(rs.getBigDecimal("total"));
        r.setReason(rs.getString("reason"));
        r.setRefundMethod(rs.getString("refund_method"));
        int s = rs.getInt("cash_session_id");
        r.setCashSessionId(rs.wasNull() ? null : s);
        r.setUserId(rs.getInt("user_id"));
        r.setUsername(rs.getString("username"));
        return r;
    }
}
