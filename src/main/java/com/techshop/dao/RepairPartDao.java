package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.RepairPart;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Pièces consommées sur un dossier (table repair_parts). */
public class RepairPartDao {

    public int insert(RepairPart p) throws SQLException {
        String sql = "INSERT INTO repair_parts (repair_id, product_id, product_unit_id, quantity, "
                + "unit_cost, unit_price, total, stock_movement_id, user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.repairId());
            ps.setInt(2, p.productId());
            ps.setObject(3, p.productUnitId());
            ps.setInt(4, p.quantity());
            ps.setBigDecimal(5, nz(p.unitCost()));
            ps.setBigDecimal(6, nz(p.unitPrice()));
            ps.setBigDecimal(7, nz(p.total()));
            ps.setObject(8, p.stockMovementId());
            ps.setObject(9, p.userId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public List<RepairPart> listByRepair(int repairId) throws SQLException {
        String sql = "SELECT rp.id, rp.repair_id, rp.product_id, p.name AS product_name, "
                + "rp.product_unit_id, rp.quantity, rp.unit_cost, rp.unit_price, rp.total, "
                + "rp.stock_movement_id, rp.added_at, rp.user_id "
                + "FROM repair_parts rp JOIN products p ON p.id = rp.product_id "
                + "WHERE rp.repair_id = ? ORDER BY rp.added_at ASC, rp.id ASC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repairId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairPart> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Retrouve une pièce par son identifiant (pour la retirer et la réapprovisionner). */
    public java.util.Optional<RepairPart> findById(long id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("SELECT rp.id, rp.repair_id, rp.product_id, "
                     + "p.name AS product_name, rp.product_unit_id, rp.quantity, rp.unit_cost, "
                     + "rp.unit_price, rp.total, rp.stock_movement_id, rp.added_at, rp.user_id "
                     + "FROM repair_parts rp JOIN products p ON p.id = rp.product_id WHERE rp.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        }
    }

    /** Supprime une ligne de pièce (le stock a été restitué par le service). */
    public void delete(long id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM repair_parts WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static RepairPart map(ResultSet rs) throws SQLException {
        Object unit = rs.getObject("product_unit_id");
        Object movement = rs.getObject("stock_movement_id");
        Timestamp added = rs.getTimestamp("added_at");
        Object user = rs.getObject("user_id");
        return new RepairPart(rs.getInt("id"), rs.getInt("repair_id"), rs.getInt("product_id"),
                rs.getString("product_name"), unit == null ? null : ((Number) unit).intValue(),
                rs.getInt("quantity"), nz(rs.getBigDecimal("unit_cost")), nz(rs.getBigDecimal("unit_price")),
                nz(rs.getBigDecimal("total")), movement == null ? null : ((Number) movement).longValue(),
                added == null ? null : added.toLocalDateTime(),
                user == null ? null : ((Number) user).intValue());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
