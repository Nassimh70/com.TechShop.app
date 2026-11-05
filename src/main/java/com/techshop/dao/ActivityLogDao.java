package com.techshop.dao;

import com.techshop.config.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Accès au journal d'activité (SPEC §5, §6).
 *
 * <p>Le module Utilisateurs et son onglet « Journal d'activité » ont été
 * retirés. L'écriture du journal reste assurée pour l'audit technique.</p>
 */
public class ActivityLogDao {

    /** Insère une entrée de journal. */
    public void insert(Integer userId, String action, String entity, Integer entityId,
                       String details, String ip) throws SQLException {
        String sql = "INSERT INTO activity_log (user_id, action, entity, entity_id, details, ip) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, action);
            ps.setString(3, entity);
            if (entityId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, entityId);
            }
            ps.setString(5, details);
            ps.setString(6, ip);
            ps.executeUpdate();
        }
    }
}
