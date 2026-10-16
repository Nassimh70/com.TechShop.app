package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.ProductUnit;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux unités sérialisées (IMEI) d'un produit (SPEC §5, §6).
 * Chaque appareil physique est identifié par un IMEI unique.
 */
public class ProductUnitDao {

    private static final String SELECT = "SELECT u.id, u.product_id, p.name AS product_name, "
            + "u.imei, u.serial_number, u.status, u.purchase_price, u.sale_id, "
            + "u.received_at, u.sold_at "
            + "FROM product_units u JOIN products p ON p.id = u.product_id ";

    /** Filtres de la liste des appareils. */
    public record Filter(Integer productId, String status, String query) {
    }

    public List<ProductUnit> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.productId() != null) {
                sql.append("AND u.product_id = ? ");
                params.add(f.productId());
            }
            if (f.status() != null && !f.status().isBlank()) {
                sql.append("AND u.status = ? ");
                params.add(f.status());
            }
            if (f.query() != null && !f.query().isBlank()) {
                sql.append("AND (u.imei LIKE ? OR u.serial_number LIKE ? OR p.name LIKE ?) ");
                String q = "%" + f.query().trim() + "%";
                params.add(q);
                params.add(q);
                params.add(q);
            }
        }
        sql.append("ORDER BY u.received_at DESC, u.id DESC LIMIT 2000");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductUnit> liste = new ArrayList<>();
                while (rs.next()) {
                    liste.add(map(rs));
                }
                return liste;
            }
        }
    }

    public List<ProductUnit> listByProduct(int productId) throws SQLException {
        return search(new Filter(productId, null, null));
    }

    public List<ProductUnit> listInStock(int productId) throws SQLException {
        return search(new Filter(productId, ProductUnit.EN_STOCK, null));
    }

    public Optional<ProductUnit> findById(long id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE u.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<ProductUnit> findByImei(String imei) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE u.imei = ?")) {
            ps.setString(1, imei);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean imeiExists(String imei) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM product_units WHERE imei = ?")) {
            ps.setString(1, imei);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public long insert(int productId, String imei, String serialNumber, String status,
                       BigDecimal purchasePrice, Integer supplierId, Timestamp receivedAt)
            throws SQLException {
        String sql = "INSERT INTO product_units (product_id, imei, serial_number, status, "
                + "purchase_price, supplier_id, received_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, productId);
            ps.setString(2, imei);
            ps.setString(3, serialNumber);
            ps.setString(4, status == null ? ProductUnit.EN_STOCK : status);
            ps.setBigDecimal(5, purchasePrice == null ? BigDecimal.ZERO : purchasePrice);
            ps.setObject(6, null);
            ps.setTimestamp(7, receivedAt == null ? new Timestamp(System.currentTimeMillis()) : receivedAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    /** Marque un appareil vendu : statut, vente, ligne de vente et date de vente. */
    public void markSold(long id, int saleId, long saleItemId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE product_units SET status = ?, sale_id = ?, sale_item_id = ?, "
                             + "sold_at = ? WHERE id = ?")) {
            ps.setString(1, ProductUnit.VENDU);
            ps.setInt(2, saleId);
            ps.setLong(3, saleItemId);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setLong(5, id);
            ps.executeUpdate();
        }
    }

    /** Change le statut d'un appareil (vente, réparation, retour...). */
    public void updateStatus(long id, String status, Integer saleId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE product_units SET status = ?, sale_id = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, saleId);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public long countByStatus(String status) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM product_units WHERE status = ?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public long countAll() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM product_units");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static ProductUnit map(ResultSet rs) throws SQLException {
        Object saleId = rs.getObject("sale_id");
        Timestamp received = rs.getTimestamp("received_at");
        Timestamp sold = rs.getTimestamp("sold_at");
        return new ProductUnit(rs.getLong("id"), rs.getInt("product_id"),
                rs.getString("product_name"), rs.getString("imei"), rs.getString("serial_number"),
                rs.getString("status"), rs.getBigDecimal("purchase_price"),
                saleId == null ? null : ((Number) saleId).intValue(),
                received == null ? null : received.toLocalDateTime(),
                sold == null ? null : sold.toLocalDateTime());
    }
}
