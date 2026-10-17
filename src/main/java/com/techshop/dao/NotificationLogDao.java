package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.NotificationLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Journal des notifications clients (table notifications_log). */
public class NotificationLogDao {

    public long insert(NotificationLog n) throws SQLException {
        String sql = "INSERT INTO notifications_log (channel, customer_id, repair_id, sale_id, "
                + "recipient, message, status, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.channel());
            ps.setObject(2, n.customerId());
            ps.setObject(3, n.repairId());
            ps.setObject(4, n.saleId());
            ps.setString(5, n.recipient());
            ps.setString(6, n.message());
            ps.setString(7, n.status() == null ? NotificationLog.SIMULE : n.status());
            ps.setObject(8, n.userId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public List<NotificationLog> listByRepair(int repairId) throws SQLException {
        String sql = "SELECT id, channel, customer_id, repair_id, sale_id, recipient, message, status, "
                + "created_at, user_id FROM notifications_log WHERE repair_id = ? "
                + "ORDER BY created_at DESC, id DESC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repairId);
            try (ResultSet rs = ps.executeQuery()) {
                List<NotificationLog> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    private static NotificationLog map(ResultSet rs) throws SQLException {
        Timestamp date = rs.getTimestamp("created_at");
        return new NotificationLog(rs.getLong("id"), rs.getString("channel"),
                intOrNull(rs.getObject("customer_id")), intOrNull(rs.getObject("repair_id")),
                intOrNull(rs.getObject("sale_id")), rs.getString("recipient"), rs.getString("message"),
                rs.getString("status"), date == null ? null : date.toLocalDateTime(),
                intOrNull(rs.getObject("user_id")));
    }

    private static Integer intOrNull(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }
}
