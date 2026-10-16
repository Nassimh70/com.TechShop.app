package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Supplier;
import com.techshop.util.ValidationException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SupplierDao {
    private static final String SELECT = "SELECT id, name, contact_name, phone, email, address, balance, notes, active, created_at FROM suppliers ";

    public record Filter(String query, Boolean inclureInactifs) {}

    public List<Supplier> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.inclureInactifs() == null || !f.inclureInactifs()) {
                sql.append("AND active = 1 ");
            }
            if (f.query() != null && !f.query().isBlank()) {
                sql.append("AND (name LIKE ? OR contact_name LIKE ? OR phone LIKE ? OR email LIKE ?) ");
                String q = "%" + f.query().trim() + "%";
                params.add(q); params.add(q); params.add(q); params.add(q);
            }
        }
        sql.append("ORDER BY name ");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Supplier> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public Optional<Supplier> byId(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(Supplier s) throws SQLException {
        String sql = "INSERT INTO suppliers (name, contact_name, phone, email, address, balance, notes, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactName());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setBigDecimal(6, s.getBalance());
            ps.setString(7, s.getNotes());
            ps.setBoolean(8, s.isActive());
            if (ps.executeUpdate() == 0) throw new SQLException("Échec insertion supplier");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(Supplier s) throws SQLException {
        String sql = "UPDATE suppliers SET name=?, contact_name=?, phone=?, email=?, address=?, balance=?, notes=?, active=? WHERE id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactName());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setBigDecimal(6, s.getBalance());
            ps.setString(7, s.getNotes());
            ps.setBoolean(8, s.isActive());
            ps.setInt(9, s.getId());
            if (ps.executeUpdate() == 0) throw new ValidationException("Fournisseur introuvable");
        }
    }

    public void updateBalance(int id, java.math.BigDecimal delta) throws SQLException {
        String sql = "UPDATE suppliers SET balance = balance + ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private Supplier map(ResultSet rs) throws SQLException {
        Supplier s = new Supplier(rs.getInt("id"), rs.getString("name"));
        s.setContactName(rs.getString("contact_name"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setBalance(rs.getBigDecimal("balance"));
        s.setNotes(rs.getString("notes"));
        s.setActive(rs.getBoolean("active"));
        s.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        return s;
    }
}
