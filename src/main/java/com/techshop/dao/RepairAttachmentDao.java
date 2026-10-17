package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.RepairAttachment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Pièces jointes d'un dossier (table repair_attachments). */
public class RepairAttachmentDao {

    public int insert(RepairAttachment a) throws SQLException {
        String sql = "INSERT INTO repair_attachments (repair_id, file_name, file_path, mime_type, "
                + "size_bytes, user_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.repairId());
            ps.setString(2, a.fileName());
            ps.setString(3, a.filePath());
            ps.setString(4, a.mimeType());
            ps.setObject(5, a.sizeBytes());
            ps.setObject(6, a.userId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM repair_attachments WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public List<RepairAttachment> listByRepair(int repairId) throws SQLException {
        String sql = "SELECT id, repair_id, file_name, file_path, mime_type, size_bytes, uploaded_at, "
                + "user_id FROM repair_attachments WHERE repair_id = ? ORDER BY uploaded_at ASC, id ASC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repairId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairAttachment> list = new ArrayList<>();
                while (rs.next()) {
                    Timestamp date = rs.getTimestamp("uploaded_at");
                    Object user = rs.getObject("user_id");
                    Object size = rs.getObject("size_bytes");
                    list.add(new RepairAttachment(rs.getInt("id"), rs.getInt("repair_id"),
                            rs.getString("file_name"), rs.getString("file_path"),
                            rs.getString("mime_type"), size == null ? null : ((Number) size).longValue(),
                            date == null ? null : date.toLocalDateTime(),
                            user == null ? null : ((Number) user).intValue()));
                }
                return list;
            }
        }
    }
}
