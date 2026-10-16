package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.GoodsReceipt;
import com.techshop.model.GoodsReceiptItem;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GoodsReceiptDao {
    public record Filter(String query, Integer supplierId, LocalDate from, LocalDate to) {}

    public List<GoodsReceipt> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT gr.id, gr.number, gr.order_id, gr.supplier_id, s.name supplier_name, gr.receipt_date, gr.total, gr.notes, gr.user_id, u.username, gr.created_at FROM goods_receipts gr JOIN suppliers s ON s.id=gr.supplier_id JOIN users u ON u.id=gr.user_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.query() != null && !f.query().isBlank()) {
                sql.append("AND (gr.number LIKE ? OR s.name LIKE ?) ");
                String q = "%" + f.query().trim() + "%";
                params.add(q); params.add(q);
            }
            if (f.supplierId() != null) {
                sql.append("AND gr.supplier_id = ? ");
                params.add(f.supplierId());
            }
            if (f.from() != null) {
                sql.append("AND gr.receipt_date >= ? ");
                params.add(java.sql.Date.valueOf(f.from()));
            }
            if (f.to() != null) {
                sql.append("AND gr.receipt_date <= ? ");
                params.add(java.sql.Date.valueOf(f.to()));
            }
        }
        sql.append("ORDER BY gr.receipt_date DESC, gr.id DESC LIMIT 100");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<GoodsReceipt> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(CAST(SUBSTRING(number,3) AS UNSIGNED)), 0)+1 FROM goods_receipts WHERE number LIKE 'B-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(GoodsReceipt gr, List<GoodsReceiptItem> items) throws SQLException {
        String sql = "INSERT INTO goods_receipts (number, order_id, supplier_id, receipt_date, total, notes, user_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, gr.getNumber() == null ? "B-" + String.format("%06d", nextNumber()) : gr.getNumber());
            if (gr.getOrderId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, gr.getOrderId());
            ps.setInt(3, gr.getSupplierId());
            ps.setDate(4, gr.getReceiptDate() == null ? java.sql.Date.valueOf(LocalDate.now()) : java.sql.Date.valueOf(gr.getReceiptDate()));
            ps.setBigDecimal(5, gr.getTotal());
            ps.setString(6, gr.getNotes());
            ps.setInt(7, gr.getUserId());
            if (ps.executeUpdate() == 0) throw new SQLException("Insertion réception échouée");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                for (GoodsReceiptItem it : items) {
                    insertItem(c, id, it);
                }
                return id;
            }
        }
    }

    private void insertItem(Connection c, int receiptId, GoodsReceiptItem it) throws SQLException {
        String sql = "INSERT INTO goods_receipt_items (receipt_id, product_id, product_unit_id, imei, quantity, unit_cost, total) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, receiptId);
            ps.setInt(2, it.getProductId());
            if (it.getProductUnitId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, it.getProductUnitId());
            ps.setString(4, it.getImei());
            ps.setInt(5, it.getQuantity());
            ps.setBigDecimal(6, it.getUnitCost());
            ps.setBigDecimal(7, it.getTotal());
            ps.executeUpdate();
        }
    }

    private GoodsReceipt map(ResultSet rs) throws SQLException {
        GoodsReceipt gr = new GoodsReceipt();
        gr.setId(rs.getInt("id"));
        gr.setNumber(rs.getString("number"));
        int oid = rs.getInt("order_id");
        gr.setOrderId(rs.wasNull() ? null : oid);
        gr.setSupplierId(rs.getInt("supplier_id"));
        gr.setSupplierName(rs.getString("supplier_name"));
        gr.setReceiptDate(rs.getDate("receipt_date").toLocalDate());
        gr.setTotal(rs.getBigDecimal("total"));
        gr.setNotes(rs.getString("notes"));
        gr.setUserId(rs.getInt("user_id"));
        gr.setUsername(rs.getString("username"));
        gr.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        return gr;
    }
}
