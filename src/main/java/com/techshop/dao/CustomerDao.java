package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Customer;
import com.techshop.util.ValidationException;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDao {
    private static final String SELECT = "SELECT id, code, full_name, phone, phone2, email, address, balance, notes, active, created_at FROM customers ";

    public record Filter(String query, Boolean withDebt, Boolean inclureInactifs) {}

    public List<Customer> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.inclureInactifs() == null || !f.inclureInactifs()) {
                sql.append("AND active = 1 ");
            }
            if (Boolean.TRUE.equals(f.withDebt())) {
                sql.append("AND balance > 0 ");
            }
            if (f.query() != null && !f.query().isBlank()) {
                sql.append("AND (code LIKE ? OR full_name LIKE ? OR phone LIKE ? OR phone2 LIKE ? OR email LIKE ?) ");
                String q = "%" + f.query().trim() + "%";
                params.add(q); params.add(q); params.add(q); params.add(q); params.add(q);
            }
        }
        sql.append("ORDER BY full_name ");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Customer> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public Optional<Customer> byId(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public long nextCodeNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)), 0)+1 FROM customers WHERE code LIKE 'C-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(Customer cu) throws SQLException {
        if (cu.getCode() == null || cu.getCode().isBlank()) {
            cu.setCode("C-" + String.format("%06d", nextCodeNumber()));
        }
        String sql = "INSERT INTO customers (code, full_name, phone, phone2, email, address, balance, notes, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cu.getCode());
            ps.setString(2, cu.getFullName());
            ps.setString(3, cu.getPhone());
            ps.setString(4, cu.getPhone2());
            ps.setString(5, cu.getEmail());
            ps.setString(6, cu.getAddress());
            ps.setBigDecimal(7, cu.getBalance() == null ? BigDecimal.ZERO : cu.getBalance());
            ps.setString(8, cu.getNotes());
            ps.setBoolean(9, cu.isActive());
            if (ps.executeUpdate() == 0) throw new SQLException("Échec insertion client");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(Customer cu) throws SQLException {
        String sql = "UPDATE customers SET full_name=?, phone=?, phone2=?, email=?, address=?, balance=?, notes=?, active=? WHERE id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cu.getFullName());
            ps.setString(2, cu.getPhone());
            ps.setString(3, cu.getPhone2());
            ps.setString(4, cu.getEmail());
            ps.setString(5, cu.getAddress());
            ps.setBigDecimal(6, cu.getBalance());
            ps.setString(7, cu.getNotes());
            ps.setBoolean(8, cu.isActive());
            ps.setInt(9, cu.getId());
            if (ps.executeUpdate() == 0) throw new ValidationException("Client introuvable");
        }
    }

    public void updateBalance(int id, BigDecimal delta) throws SQLException {
        String sql = "UPDATE customers SET balance = balance + ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setCode(rs.getString("code"));
        c.setFullName(rs.getString("full_name"));
        c.setPhone(rs.getString("phone"));
        c.setPhone2(rs.getString("phone2"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setBalance(rs.getBigDecimal("balance"));
        c.setNotes(rs.getString("notes"));
        c.setActive(rs.getBoolean("active"));
        c.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        return c;
    }
}
