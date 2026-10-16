package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.PurchaseOrder;
import com.techshop.model.PurchaseOrderItem;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PurchaseOrderDao {
    public record Filter(String query, String status, Integer supplierId, LocalDate from, LocalDate to) {}

    public List<PurchaseOrder> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT po.id, po.number, po.supplier_id, s.name supplier_name, po.order_date, po.expected_date, po.status, po.subtotal, po.discount, po.tax, po.total, po.paid_amount, po.notes, po.user_id, u.username, po.created_at FROM purchase_orders po JOIN suppliers s ON s.id=po.supplier_id JOIN users u ON u.id=po.user_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.query() != null && !f.query().isBlank()) {
                sql.append("AND (po.number LIKE ? OR s.name LIKE ?) ");
                String q = "%" + f.query().trim() + "%";
                params.add(q); params.add(q);
            }
            if (f.status() != null && !f.status().isBlank()) {
                sql.append("AND po.status = ? ");
                params.add(f.status());
            }
            if (f.supplierId() != null) {
                sql.append("AND po.supplier_id = ? ");
                params.add(f.supplierId());
            }
            if (f.from() != null) {
                sql.append("AND po.order_date >= ? ");
                params.add(java.sql.Date.valueOf(f.from()));
            }
            if (f.to() != null) {
                sql.append("AND po.order_date <= ? ");
                params.add(java.sql.Date.valueOf(f.to()));
            }
        }
        sql.append("ORDER BY po.order_date DESC, po.id DESC LIMIT 100");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<PurchaseOrder> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public Optional<PurchaseOrder> byId(int id) throws SQLException {
        String sql = "SELECT po.id, po.number, po.supplier_id, s.name supplier_name, po.order_date, po.expected_date, po.status, po.subtotal, po.discount, po.tax, po.total, po.paid_amount, po.notes, po.user_id, u.username, po.created_at FROM purchase_orders po JOIN suppliers s ON s.id=po.supplier_id JOIN users u ON u.id=po.user_id WHERE po.id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                PurchaseOrder po = map(rs);
                po.getItems().addAll(items(po.getId()));
                return Optional.of(po);
            }
        }
    }

    private List<PurchaseOrderItem> items(int orderId) throws SQLException {
        String sql = "SELECT poi.id, poi.order_id, poi.product_id, p.ref, p.name, poi.quantity, poi.quantity_received, poi.unit_cost, poi.discount, poi.total FROM purchase_order_items poi JOIN products p ON p.id=poi.product_id WHERE poi.order_id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PurchaseOrderItem> list = new ArrayList<>();
                while (rs.next()) {
                    PurchaseOrderItem it = new PurchaseOrderItem();
                    it.setId(rs.getInt("id"));
                    it.setOrderId(rs.getInt("order_id"));
                    it.setProductId(rs.getInt("product_id"));
                    it.setProductRef(rs.getString("ref"));
                    it.setProductName(rs.getString("name"));
                    it.setQuantity(rs.getInt("quantity"));
                    it.setQuantityReceived(rs.getInt("quantity_received"));
                    it.setUnitCost(rs.getBigDecimal("unit_cost"));
                    it.setDiscount(rs.getBigDecimal("discount"));
                    it.setTotal(rs.getBigDecimal("total"));
                    list.add(it);
                }
                return list;
            }
        }
    }

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(CAST(SUBSTRING(number,3) AS UNSIGNED)), 0)+1 FROM purchase_orders WHERE number LIKE 'A-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(PurchaseOrder po) throws SQLException {
        String sql = "INSERT INTO purchase_orders (number, supplier_id, order_date, expected_date, status, subtotal, discount, tax, total, paid_amount, notes, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, po.getNumber() == null ? "A-" + String.format("%06d", nextNumber()) : po.getNumber());
            ps.setInt(2, po.getSupplierId());
            ps.setDate(3, po.getOrderDate() == null ? java.sql.Date.valueOf(LocalDate.now()) : java.sql.Date.valueOf(po.getOrderDate()));
            ps.setDate(4, po.getExpectedDate() == null ? null : java.sql.Date.valueOf(po.getExpectedDate()));
            ps.setString(5, po.getStatus().name());
            ps.setBigDecimal(6, po.getSubtotal());
            ps.setBigDecimal(7, po.getDiscount());
            ps.setBigDecimal(8, po.getTax());
            ps.setBigDecimal(9, po.getTotal());
            ps.setBigDecimal(10, po.getPaidAmount());
            ps.setString(11, po.getNotes());
            ps.setInt(12, po.getUserId());
            if (ps.executeUpdate() == 0) throw new SQLException("Insertion commande échouée");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                for (PurchaseOrderItem it : po.getItems()) {
                    insertItem(c, id, it);
                }
                return id;
            }
        }
    }

    private void insertItem(Connection c, int orderId, PurchaseOrderItem it) throws SQLException {
        String sql = "INSERT INTO purchase_order_items (order_id, product_id, quantity, quantity_received, unit_cost, discount, total) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, it.getProductId());
            ps.setInt(3, it.getQuantity());
            ps.setInt(4, it.getQuantityReceived());
            ps.setBigDecimal(5, it.getUnitCost());
            ps.setBigDecimal(6, it.getDiscount());
            ps.setBigDecimal(7, it.getTotal());
            ps.executeUpdate();
        }
    }

    public void updateStatus(int id, PurchaseOrder.Status status) throws SQLException {
        String sql = "UPDATE purchase_orders SET status=? WHERE id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void incrementReceived(int orderId, int productId, int qty) throws SQLException {
        String sql = "UPDATE purchase_order_items SET quantity_received = quantity_received + ? WHERE order_id=? AND product_id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, orderId);
            ps.setInt(3, productId);
            ps.executeUpdate();
        }
    }

    private PurchaseOrder map(ResultSet rs) throws SQLException {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(rs.getInt("id"));
        po.setNumber(rs.getString("number"));
        po.setSupplierId(rs.getInt("supplier_id"));
        po.setSupplierName(rs.getString("supplier_name"));
        po.setOrderDate(rs.getDate("order_date").toLocalDate());
        if (rs.getDate("expected_date") != null) po.setExpectedDate(rs.getDate("expected_date").toLocalDate());
        po.setStatus(PurchaseOrder.Status.valueOf(rs.getString("status")));
        po.setSubtotal(rs.getBigDecimal("subtotal"));
        po.setDiscount(rs.getBigDecimal("discount"));
        po.setTax(rs.getBigDecimal("tax"));
        po.setTotal(rs.getBigDecimal("total"));
        po.setPaidAmount(rs.getBigDecimal("paid_amount"));
        po.setNotes(rs.getString("notes"));
        po.setUserId(rs.getInt("user_id"));
        po.setUsername(rs.getString("username"));
        po.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        return po;
    }
}
