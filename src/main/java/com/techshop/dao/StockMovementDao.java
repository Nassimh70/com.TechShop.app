package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.StockMovement;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès à l'historique des mouvements de stock (SPEC §5, §6.2).
 * Toute variation du stock est écrite ici par le {@code StockService},
 * dans la même transaction que la mise à jour de la quantité du produit.
 */
public class StockMovementDao {

    private static final String SELECT = "SELECT m.id, m.product_id, p.ref AS product_ref, "
            + "p.name AS product_name, p.barcode, m.product_unit_id, u.imei AS unit_imei, "
            + "m.type, m.quantity, m.quantity_before, m.quantity_after, m.unit_cost, m.reason, "
            + "m.reference_type, m.reference_id, m.user_id, us.username, m.movement_date "
            + "FROM stock_movements m "
            + "JOIN products p ON p.id = m.product_id "
            + "LEFT JOIN product_units u ON u.id = m.product_unit_id "
            + "LEFT JOIN users us ON us.id = m.user_id ";

    /** Critères d'historique des mouvements. */
    public record Filter(Integer productId, String type, String referenceType, String query,
                         LocalDateTime from, LocalDateTime to) {
    }

    /** Insère un mouvement et retourne son identifiant. */
    public long insert(int productId, Integer productUnitId, String type, int quantity,
                       int quantityBefore, int quantityAfter, BigDecimal unitCost, String reason,
                       String referenceType, Integer referenceId, Integer userId,
                       LocalDateTime movementDate) throws SQLException {
        String sql = "INSERT INTO stock_movements (product_id, product_unit_id, type, quantity, "
                + "quantity_before, quantity_after, unit_cost, reason, reference_type, reference_id, "
                + "user_id, movement_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, productId);
            ps.setObject(2, productUnitId);
            ps.setString(3, type);
            ps.setInt(4, quantity);
            ps.setInt(5, quantityBefore);
            ps.setInt(6, quantityAfter);
            ps.setBigDecimal(7, unitCost);
            ps.setString(8, reason);
            ps.setString(9, referenceType);
            ps.setObject(10, referenceId);
            ps.setInt(11, userId == null ? 1 : userId);
            ps.setTimestamp(12, Timestamp.valueOf(
                    movementDate == null ? LocalDateTime.now() : movementDate));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    public List<StockMovement> search(Filter f, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, f);
        sql.append("ORDER BY m.movement_date DESC, m.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<StockMovement> liste = new ArrayList<>();
                while (rs.next()) {
                    liste.add(map(rs));
                }
                return liste;
            }
        }
    }

    public long count(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM stock_movements m "
                + "JOIN products p ON p.id = m.product_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, f);
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public List<StockMovement> recent(int limit) throws SQLException {
        return search(null, limit, 0);
    }

    public List<StockMovement> listByProduct(int productId) throws SQLException {
        return search(new Filter(productId, null, null, null, null, null), 200, 0);
    }

    private static void appendWhere(StringBuilder sql, List<Object> params, Filter f) {
        if (f == null) {
            return;
        }
        if (f.productId() != null) {
            sql.append("AND m.product_id = ? ");
            params.add(f.productId());
        }
        if (f.type() != null && !f.type().isBlank()) {
            sql.append("AND m.type = ? ");
            params.add(f.type());
        }
        if (f.referenceType() != null && !f.referenceType().isBlank()) {
            sql.append("AND m.reference_type = ? ");
            params.add(f.referenceType());
        }
        if (f.query() != null && !f.query().isBlank()) {
            sql.append("AND (p.name LIKE ? OR p.ref LIKE ? OR m.reason LIKE ?) ");
            String q = "%" + f.query().trim() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (f.from() != null) {
            sql.append("AND m.movement_date >= ? ");
            params.add(Timestamp.valueOf(f.from()));
        }
        if (f.to() != null) {
            sql.append("AND m.movement_date <= ? ");
            params.add(Timestamp.valueOf(f.to()));
        }
    }

    private static StockMovement map(ResultSet rs) throws SQLException {
        Timestamp date = rs.getTimestamp("movement_date");
        return new StockMovement(
                rs.getLong("id"),
                rs.getInt("product_id"),
                rs.getString("product_ref"),
                rs.getString("product_name"),
                asInt(rs.getObject("product_unit_id")),
                rs.getString("unit_imei"),
                rs.getString("type"),
                rs.getInt("quantity"),
                rs.getInt("quantity_before"),
                rs.getInt("quantity_after"),
                rs.getBigDecimal("unit_cost"),
                rs.getString("reason"),
                rs.getString("reference_type"),
                asInt(rs.getObject("reference_id")),
                asInt(rs.getObject("user_id")),
                rs.getString("username"),
                date == null ? null : date.toLocalDateTime(),
                rs.getString("barcode"));
    }

    private static Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
