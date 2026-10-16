package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Sale;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux ventes (SPEC §4.3, §4.4, §5). JDBC pur, requêtes préparées.
 * Les montants sont agrégés côté SQL ; la recherche est filtrée (texte, dates,
 * client, vendeur, statut de règlement, mode de paiement) et paginée.
 */
public class SaleDao {

    private static final String SELECT = "SELECT s.id, s.number, s.sale_date, s.customer_id, "
            + "c.full_name AS customer_name, s.user_id, u.full_name AS username, s.cash_session_id, "
            + "s.subtotal, s.discount, s.tax, s.total, s.paid_amount, s.due_amount, s.profit, "
            + "s.payment_status, s.status, s.note, s.created_at, "
            + "(SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.id) AS item_count, "
            + "(SELECT GROUP_CONCAT(DISTINCT sp.method ORDER BY sp.method) "
            + "   FROM sale_payments sp WHERE sp.sale_id = s.id) AS methods, "
            + "(SELECT COALESCE(SUM(sri.quantity), 0) FROM sale_return_items sri "
            + "   JOIN sale_returns sr ON sr.id = sri.return_id WHERE sr.sale_id = s.id) AS returned_qty "
            + "FROM sales s "
            + "LEFT JOIN customers c ON c.id = s.customer_id "
            + "LEFT JOIN users u ON u.id = s.user_id ";

    /** Filtres de la liste des ventes. */
    public record Filter(String query, LocalDate from, LocalDate to, Integer customerId,
                         Integer userId, String paymentStatus, String method) {
    }

    /** Point quotidien (courbe du tableau de bord). */
    public record DayPoint(LocalDate day, BigDecimal total, long count) {
    }

    /** Répartition par mode de paiement (anneau). */
    public record MethodPoint(String method, BigDecimal total) {
    }

    /** Produit le plus vendu (top produits). */
    public record TopProduct(String name, long quantity, BigDecimal total) {
    }

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(MAX(CAST(SUBSTRING(number, 3) AS UNSIGNED)), 0) + 1 "
                             + "FROM sales WHERE number LIKE 'V-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    /** Insère l'en-tête d'une vente et renvoie son identifiant. */
    public int insert(Sale s) throws SQLException {
        if (s.getNumber() == null || s.getNumber().isBlank()) {
            s.setNumber("V-" + String.format("%06d", nextNumber()));
        }
        String sql = "INSERT INTO sales (number, sale_date, customer_id, user_id, cash_session_id, "
                + "subtotal, discount, tax, total, paid_amount, due_amount, profit, payment_status, "
                + "status, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getNumber());
            ps.setTimestamp(2, Timestamp.valueOf(
                    s.getSaleDate() == null ? LocalDateTime.now() : s.getSaleDate()));
            ps.setObject(3, s.getCustomerId());
            ps.setInt(4, s.getUserId());
            ps.setObject(5, s.getCashSessionId());
            ps.setBigDecimal(6, s.getSubtotal());
            ps.setBigDecimal(7, s.getDiscount());
            ps.setBigDecimal(8, s.getTax());
            ps.setBigDecimal(9, s.getTotal());
            ps.setBigDecimal(10, s.getPaidAmount());
            ps.setBigDecimal(11, s.getDueAmount());
            ps.setBigDecimal(12, s.getProfit());
            ps.setString(13, s.getPaymentStatus());
            ps.setString(14, s.getStatus());
            ps.setString(15, s.getNote());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Insertion de la vente échouée");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                s.setId(id);
                return id;
            }
        }
    }

    public Optional<Sale> byId(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE s.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Sale> search(Filter f, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, f);
        sql.append("ORDER BY s.sale_date DESC, s.id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sale> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    public long count(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sales s "
                + "LEFT JOIN customers c ON c.id = s.customer_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, f);
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private void appendFilters(StringBuilder sql, List<Object> params, Filter f) {
        if (f == null) {
            return;
        }
        if (f.query() != null && !f.query().isBlank()) {
            sql.append("AND (s.number LIKE ? OR c.full_name LIKE ?) ");
            String like = "%" + f.query().trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (f.from() != null) {
            sql.append("AND s.sale_date >= ? ");
            params.add(Timestamp.valueOf(f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            sql.append("AND s.sale_date < ? ");
            params.add(Timestamp.valueOf(f.to().plusDays(1).atStartOfDay()));
        }
        if (f.customerId() != null) {
            sql.append("AND s.customer_id = ? ");
            params.add(f.customerId());
        }
        if (f.userId() != null) {
            sql.append("AND s.user_id = ? ");
            params.add(f.userId());
        }
        if (f.paymentStatus() != null && !f.paymentStatus().isBlank()) {
            sql.append("AND s.payment_status = ? ");
            params.add(f.paymentStatus());
        }
        if (f.method() != null && !f.method().isBlank()) {
            sql.append("AND EXISTS (SELECT 1 FROM sale_payments sp WHERE sp.sale_id = s.id "
                    + "AND sp.method = ?) ");
            params.add(f.method());
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    public List<Sale> recent(int limit) throws SQLException {
        String sql = SELECT + "ORDER BY s.sale_date DESC, s.id DESC LIMIT ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<Sale> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Met à jour les montants réglés / dus après un retour. */
    public void updateSettlement(int id, BigDecimal paid, BigDecimal due, String paymentStatus)
            throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sales SET paid_amount = ?, due_amount = ?, payment_status = ? "
                             + "WHERE id = ?")) {
            ps.setBigDecimal(1, paid);
            ps.setBigDecimal(2, due);
            ps.setString(3, paymentStatus);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    public void setStatus(int id, String status) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE sales SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------- statistiques

    public long countBetween(LocalDateTime from, LocalDateTime to) throws SQLException {
        return scalarLong("SELECT COUNT(*) FROM sales WHERE sale_date >= ? AND sale_date < ? "
                + "AND status <> 'ANNULEE'", from, to);
    }

    public BigDecimal sumTotalBetween(LocalDateTime from, LocalDateTime to) throws SQLException {
        return scalarMoney("SELECT COALESCE(SUM(total), 0) FROM sales WHERE sale_date >= ? "
                + "AND sale_date < ? AND status <> 'ANNULEE'", from, to);
    }

    public BigDecimal sumProfitBetween(LocalDateTime from, LocalDateTime to) throws SQLException {
        return scalarMoney("SELECT COALESCE(SUM(profit), 0) FROM sales WHERE sale_date >= ? "
                + "AND sale_date < ? AND status <> 'ANNULEE'", from, to);
    }

    public BigDecimal sumDueBetween(LocalDateTime from, LocalDateTime to) throws SQLException {
        return scalarMoney("SELECT COALESCE(SUM(due_amount), 0) FROM sales WHERE sale_date >= ? "
                + "AND sale_date < ? AND status <> 'ANNULEE'", from, to);
    }

    /** Total des ventes rattachées à une session de caisse. */
    public BigDecimal sumTotalBySession(int sessionId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(total), 0) FROM sales WHERE cash_session_id = ? "
                             + "AND status <> 'ANNULEE'")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : null;
                return v == null ? BigDecimal.ZERO : v;
            }
        }
    }

    /** Nombre de ventes rattachées à une session de caisse. */
    public long countBySession(int sessionId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM sales WHERE cash_session_id = ? AND status <> 'ANNULEE'")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Ventes par jour sur une période (courbe). */
    public List<DayPoint> salesByDay(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(sale_date) AS d, COALESCE(SUM(total), 0) AS t, COUNT(*) AS n "
                + "FROM sales WHERE sale_date >= ? AND sale_date < ? AND status <> 'ANNULEE' "
                + "GROUP BY DATE(sale_date) ORDER BY d";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                List<DayPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new DayPoint(rs.getDate("d").toLocalDate(),
                            rs.getBigDecimal("t"), rs.getLong("n")));
                }
                return list;
            }
        }
    }

    /** Répartition des encaissements par mode de paiement (anneau). */
    public List<MethodPoint> paymentBreakdown(LocalDateTime from, LocalDateTime to)
            throws SQLException {
        String sql = "SELECT sp.method, COALESCE(SUM(sp.amount), 0) AS t FROM sale_payments sp "
                + "JOIN sales s ON s.id = sp.sale_id "
                + "WHERE s.sale_date >= ? AND s.sale_date < ? AND s.status <> 'ANNULEE' "
                + "GROUP BY sp.method ORDER BY t DESC";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                List<MethodPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new MethodPoint(rs.getString("method"), rs.getBigDecimal("t")));
                }
                return list;
            }
        }
    }

    /** Produits les plus vendus sur une période. */
    public List<TopProduct> topProducts(LocalDateTime from, LocalDateTime to, int limit)
            throws SQLException {
        String sql = "SELECT p.name AS name, COALESCE(SUM(si.quantity), 0) AS q, "
                + "COALESCE(SUM(si.total), 0) AS t FROM sale_items si "
                + "JOIN sales s ON s.id = si.sale_id JOIN products p ON p.id = si.product_id "
                + "WHERE s.sale_date >= ? AND s.sale_date < ? AND s.status <> 'ANNULEE' "
                + "GROUP BY p.id, p.name ORDER BY q DESC, t DESC LIMIT ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<TopProduct> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new TopProduct(rs.getString("name"), rs.getLong("q"),
                            rs.getBigDecimal("t")));
                }
                return list;
            }
        }
    }

    private long scalarLong(String sql, LocalDateTime from, LocalDateTime to) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private BigDecimal scalarMoney(String sql, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                BigDecimal v = rs.next() ? rs.getBigDecimal(1) : null;
                return v == null ? BigDecimal.ZERO : v;
            }
        }
    }

    static Sale map(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setId(rs.getInt("id"));
        s.setNumber(rs.getString("number"));
        Timestamp d = rs.getTimestamp("sale_date");
        s.setSaleDate(d == null ? null : d.toLocalDateTime());
        int customerId = rs.getInt("customer_id");
        s.setCustomerId(rs.wasNull() ? null : customerId);
        s.setCustomerName(rs.getString("customer_name"));
        s.setUserId(rs.getInt("user_id"));
        s.setUsername(rs.getString("username"));
        int session = rs.getInt("cash_session_id");
        s.setCashSessionId(rs.wasNull() ? null : session);
        s.setSubtotal(rs.getBigDecimal("subtotal"));
        s.setDiscount(rs.getBigDecimal("discount"));
        s.setTax(rs.getBigDecimal("tax"));
        s.setTotal(rs.getBigDecimal("total"));
        s.setPaidAmount(rs.getBigDecimal("paid_amount"));
        s.setDueAmount(rs.getBigDecimal("due_amount"));
        s.setProfit(rs.getBigDecimal("profit"));
        s.setPaymentStatus(rs.getString("payment_status"));
        s.setStatus(rs.getString("status"));
        s.setNote(rs.getString("note"));
        Timestamp created = rs.getTimestamp("created_at");
        s.setCreatedAt(created == null ? null : created.toLocalDateTime());
        try {
            s.setItemCount(rs.getInt("item_count"));
            s.setMethods(rs.getString("methods"));
            s.setReturnedQuantity(rs.getInt("returned_qty"));
        } catch (SQLException ignored) {
            // colonnes absentes (requêtes partielles)
        }
        return s;
    }
}
