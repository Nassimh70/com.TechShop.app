package com.techshop.dao;

import com.techshop.config.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Accès à la table « settings » (SPEC §5) : paramètres du magasin, des ventes,
 * de l'impression et de l'apparence, stockés sous forme clé / valeur.
 */
public class SettingDao {

    public Map<String, String> all() throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT cle, valeur FROM settings")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("cle"), rs.getString("valeur"));
                }
            }
        }
        return map;
    }

    public Optional<String> get(String cle) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT valeur FROM settings WHERE cle = ?")) {
            ps.setString(1, cle);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void set(String cle, String valeur) throws SQLException {
        String sql = "INSERT INTO settings (cle, valeur) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cle);
            ps.setString(2, valeur);
            ps.executeUpdate();
        }
    }
}
