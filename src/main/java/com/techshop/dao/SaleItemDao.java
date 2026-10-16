package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.SaleItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux lignes de vente (sale_items). Chaque ligne fige le prix et le coût
 * au moment de la vente, ainsi que l'appareil vendu (IMEI) le cas échéant.
 */
public class SaleItemDao {

    public long insert(SaleItem it) throws SQLException {
        String sql = "INSERT INTO sale_items (sale_id, product_id, product_unit_id, description, "
                + "quantity, unit_price, unit_cost, discount, vat_rate, total, warranty_months) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, it.getSaleId());
            ps.setInt(2, it.getProductId());
            ps.setObject(3, it.getProductUnitId());
            ps.setString(4, it.getDescription());
            ps.setInt(5, it.getQuantity());
            ps.setBigDecimal(6, it.getUnitPrice());
            ps.setBigDecimal(7, it.getUnitCost());
            ps.setBigDecimal(8, it.getDiscount());
            ps.setBigDecimal(9, it.getVatRate());
            ps.setBigDecimal(10, it.getTotal());
            ps.setInt(11, it.getWarrantyMonths());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Insertion de la ligne de vente échouée");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                it.setId((int) id);
                return id;
            }
        }
    }

    public void update(int id, int quantity, BigDecimal unitPrice, BigDecimal discount, BigDecimal total)
            throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sale_items SET quantity = ?, unit_price = ?, discount = ?, total = ? "
                             + "WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setBigDecimal(2, unitPrice);
            ps.setBigDecimal(3, discount);
            ps.setBigDecimal(4, total);
            ps.setInt(5, id);
            ps.executeUpdate();
        }
    }

    public List<SaleItem> listBySale(int saleId) throws SQLException {
        String sql = "SELECT si.id, si.sale_id, si.product_id, p.name AS product_name, "
                + "p.ref AS product_ref, si.product_unit_id, u.imei, si.description, si.quantity, "
                + "si.unit_price, si.unit_cost, si.discount, si.vat_rate, si.total, "
                + "si.warranty_months, "
                + "(SELECT COALESCE(SUM(sri.quantity), 0) FROM sale_return_items sri "
                + "   WHERE sri.sale_item_id = si.id) AS returned_qty "
                + "FROM sale_items si JOIN products p ON p.id = si.product_id "
                + "LEFT JOIN product_units u ON u.id = si.product_unit_id "
                + "WHERE si.sale_id = ? ORDER BY si.id";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SaleItem> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    static SaleItem map(ResultSet rs) throws SQLException {
        SaleItem it = new SaleItem();
        it.setId(rs.getInt("id"));
        it.setSaleId(rs.getInt("sale_id"));
        it.setProductId(rs.getInt("product_id"));
        it.setProductName(rs.getString("product_name"));
        it.setProductRef(rs.getString("product_ref"));
        int unitId = rs.getInt("product_unit_id");
        it.setProductUnitId(rs.wasNull() ? null : unitId);
        it.setImei(rs.getString("imei"));
        it.setDescription(rs.getString("description"));
        it.setQuantity(rs.getInt("quantity"));
        it.setUnitPrice(rs.getBigDecimal("unit_price"));
        it.setUnitCost(rs.getBigDecimal("unit_cost"));
        it.setDiscount(rs.getBigDecimal("discount"));
        it.setVatRate(rs.getBigDecimal("vat_rate"));
        it.setTotal(rs.getBigDecimal("total"));
        it.setWarrantyMonths(rs.getInt("warranty_months"));
        try {
            it.setReturnedQuantity(rs.getInt("returned_qty"));
        } catch (SQLException ignored) {
            // colonne absente
        }
        return it;
    }
}
