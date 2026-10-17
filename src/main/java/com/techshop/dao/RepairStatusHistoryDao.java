package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.RepairStatusHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Chronologie des statuts d'un dossier (table repair_status_history). */
public class RepairStatusHistoryDao {

    public long insert(int repairId, String oldStatus, String newStatus, String comment, Integer userId)
            throws SQLException {
        String sql = "INSERT INTO repair_status_history (repair_id, old_status, new_status, comment, "
                + "user_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, repairId);
            ps.setString(2, oldStatus);
            ps.setString(3, newStatus);
            ps.setString(4, comment);
            if (userId == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, userId);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public List<RepairStatusHistory> listByRepair(int repairId) throws SQLException {
        String sql = "SELECT h.id, h.repair_id, h.old_status, h.new_status, h.comment, h.user_id, "
                + "u.full_name AS user_name, h.changed_at "
                + "FROM repair_status_history h LEFT JOIN users u ON u.id = h.user_id "
                + "WHERE h.repair_id = ? ORDER BY h.changed_at ASC, h.id ASC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repairId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairStatusHistory> list = new ArrayList<>();
                while (rs.next()) {
                    Object uid = rs.getObject("user_id");
                    Timestamp at = rs.getTimestamp("changed_at");
                    list.add(new RepairStatusHistory(rs.getLong("id"), rs.getInt("repair_id"),
                            rs.getString("old_status"), rs.getString("new_status"),
                            rs.getString("comment"), uid == null ? null : ((Number) uid).intValue(),
                            rs.getString("user_name"),
                            at == null ? null : at.toLocalDateTime()));
                }
                return list;
            }
        }
    }
}
