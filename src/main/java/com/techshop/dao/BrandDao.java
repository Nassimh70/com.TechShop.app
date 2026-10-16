package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Brand;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Accès aux marques de produits (SPEC §4.5, §5). Nom unique, actif/inactif. */
public class BrandDao {

    public List<Brand> list(boolean inclureInactives) throws SQLException {
        String sql = "SELECT id, name, active FROM brands "
                + (inclureInactives ? "" : "WHERE active = 1 ") + "ORDER BY name";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Brand> liste = new ArrayList<>();
            while (rs.next()) {
                liste.add(map(rs));
            }
            return liste;
        }
    }

    public Optional<Brand> findById(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT id, name, active FROM brands WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(String name) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO brands (name, active) VALUES (?, 1)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(int id, String name, boolean active) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE brands SET name = ?, active = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, active ? 1 : 0);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public void setActive(int id, boolean active) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE brands SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public boolean nameExists(String name, int excludeId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM brands WHERE name = ? AND id <> ?")) {
            ps.setString(1, name);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public int productCount(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE brand_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Brand map(ResultSet rs) throws SQLException {
        return new Brand(rs.getInt("id"), rs.getString("name"), rs.getInt("active") == 1);
    }
}