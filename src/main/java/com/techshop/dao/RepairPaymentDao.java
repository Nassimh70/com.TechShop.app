package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.RepairPayment;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Acomptes et règlements d'un dossier (table repair_payments). */
public class RepairPaymentDao {

    public int insert(RepairPayment p) throws SQLException {
        String sql = "INSERT INTO repair_payments (repair_id, payment_date, method, amount, "
                + "cash_session_id, note, user_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.repairId());
            ps.setTimestamp(2, Timestamp.valueOf(p.paymentDate() == null
                    ? java.time.LocalDateTime.now() : p.paymentDate()));
            ps.setString(3, p.method());
            ps.setBigDecimal(4, p.amount() == null ? BigDecimal.ZERO : p.amount());
            ps.setObject(5, p.cashSessionId());
            ps.setString(6, p.note());
            ps.setObject(7, p.userId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public List<RepairPayment> listByRepair(int repairId) throws SQLException {
        String sql = "SELECT id, repair_id, payment_date, method, amount, cash_session_id, note, user_id "
                + "FROM repair_payments WHERE repair_id = ? ORDER BY payment_date ASC, id ASC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repairId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairPayment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Somme des règlements déjà encaissés pour un dossier. */
    public BigDecimal sumByRepair(int repairId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM repair_payments WHERE repair_id = ?")) {
            ps.setInt(1, repairId);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : null;
                return v == null ? BigDecimal.ZERO : v;
            }
        }
    }

    private static RepairPayment map(ResultSet rs) throws SQLException {
        Timestamp date = rs.getTimestamp("payment_date");
        Object session = rs.getObject("cash_session_id");
        Object user = rs.getObject("user_id");
        return new RepairPayment(rs.getInt("id"), rs.getInt("repair_id"),
                date == null ? null : date.toLocalDateTime(), rs.getString("method"),
                rs.getBigDecimal("amount"), session == null ? null : ((Number) session).intValue(),
                rs.getString("note"), user == null ? null : ((Number) user).intValue());
    }
}
