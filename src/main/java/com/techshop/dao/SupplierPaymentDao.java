package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.SupplierPayment;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SupplierPaymentDao {
    public record Filter(Integer supplierId, LocalDateTime from, LocalDateTime to) {}

    public List<SupplierPayment> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT sp.id, sp.number, sp.supplier_id, s.name supplier_name, sp.amount, sp.payment_method, sp.payment_date, sp.cash_session_id, sp.note, sp.user_id, u.username FROM supplier_payments sp JOIN suppliers s ON s.id=sp.supplier_id JOIN users u ON u.id=sp.user_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.supplierId() != null) {
                sql.append("AND sp.supplier_id = ? ");
                params.add(f.supplierId());
            }
            if (f.from() != null) {
                sql.append("AND sp.payment_date >= ? ");
                params.add(java.sql.Timestamp.valueOf(f.from()));
            }
            if (f.to() != null) {
                sql.append("AND sp.payment_date <= ? ");
                params.add(java.sql.Timestamp.valueOf(f.to()));
            }
        }
        sql.append("ORDER BY sp.payment_date DESC, sp.id DESC LIMIT 100");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<SupplierPayment> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(CAST(SUBSTRING(number,3) AS UNSIGNED)), 0)+1 FROM supplier_payments WHERE number LIKE 'PF-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(SupplierPayment pmt) throws SQLException {
        String sql = "INSERT INTO supplier_payments (number, supplier_id, amount, payment_method, payment_date, cash_session_id, note, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, pmt.getNumber() == null ? "PF-" + String.format("%06d", nextNumber()) : pmt.getNumber());
            ps.setInt(2, pmt.getSupplierId());
            ps.setBigDecimal(3, pmt.getAmount());
            ps.setString(4, pmt.getPaymentMethod().name());
            ps.setTimestamp(5, pmt.getPaymentDate() == null ? new Timestamp(System.currentTimeMillis()) : Timestamp.valueOf(pmt.getPaymentDate()));
            if (pmt.getCashSessionId() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, pmt.getCashSessionId());
            ps.setString(7, pmt.getNote());
            ps.setInt(8, pmt.getUserId());
            if (ps.executeUpdate() == 0) throw new SQLException("Insertion paiement échouée");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private SupplierPayment map(ResultSet rs) throws SQLException {
        SupplierPayment p = new SupplierPayment();
        p.setId(rs.getInt("id"));
        p.setNumber(rs.getString("number"));
        p.setSupplierId(rs.getInt("supplier_id"));
        p.setSupplierName(rs.getString("supplier_name"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setPaymentMethod(SupplierPayment.Method.valueOf(rs.getString("payment_method")));
        p.setPaymentDate(rs.getTimestamp("payment_date").toLocalDateTime());
        int cs = rs.getInt("cash_session_id");
        p.setCashSessionId(rs.wasNull() ? null : cs);
        p.setNote(rs.getString("note"));
        p.setUserId(rs.getInt("user_id"));
        p.setUsername(rs.getString("username"));
        return p;
    }
}
