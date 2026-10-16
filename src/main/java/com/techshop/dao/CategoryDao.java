package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux catégories de produits (SPEC §4.5, §5).
 * Suppression logique (colonne active) ; le nom est unique.
 */
public class CategoryDao {

    private static final String SELECT = "SELECT c.id, c.name, c.parent_id, c.active, "
            + "p.name AS parent_name FROM categories c "
            + "LEFT JOIN categories p ON p.id = c.parent_id ";

    public List<Category> list(boolean inclureInactives) throws SQLException {
        String sql = SELECT + (inclureInactives ? "" : "WHERE c.active = 1 ") + "ORDER BY c.name";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Category> liste = new ArrayList<>();
            while (rs.next()) {
                liste.add(map(rs));
            }
            return liste;
        }
    }

    public Optional<Category> findById(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE c.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(String name, Integer parentId) throws SQLException {
        String sql = "INSERT INTO categories (name, parent_id, active) VALUES (?, ?, 1)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setObject(2, parentId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(int id, String name, Integer parentId, boolean active) throws SQLException {
        String sql = "UPDATE categories SET name = ?, parent_id = ?, active = ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setObject(2, parentId);
            ps.setInt(3, active ? 1 : 0);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public void setActive(int id, boolean active) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE categories SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public boolean nameExists(String name, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM categories WHERE name = ? AND id <> ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** Nombre de produits rattachés (pour interdire la suppression). */
    public int productCount(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE category_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Category map(ResultSet rs) throws SQLException {
        Object parent = rs.getObject("parent_id");
        return new Category(rs.getInt("id"), rs.getString("name"),
                parent == null ? null : ((Number) parent).intValue(),
                rs.getString("parent_name"), rs.getInt("active") == 1);
    }
}