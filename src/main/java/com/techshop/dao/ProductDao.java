package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Product;
import com.techshop.model.StockStatus;

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
 * Accès au catalogue produits (SPEC §4.5, §5). JDBC pur, requêtes préparées.
 * Recherche filtrée (texte, catégorie, marque, statut de stock) et paginée,
 * tri sur une colonne autorisée (liste blanche, jamais de concaténation brute).
 */
public class ProductDao {

    private static final String SELECT = "SELECT p.id, p.ref, p.barcode, p.name, p.description, "
            + "p.category_id, c.name AS category_name, p.brand_id, b.name AS brand_name, "
            + "p.unit, p.purchase_price, p.avg_cost, p.sale_price, p.vat_rate, p.alert_threshold, "
            + "p.quantity, p.track_imei, p.warranty_months, p.image_path, p.active, "
            + "p.created_at, p.updated_at "
            + "FROM products p "
            + "LEFT JOIN categories c ON c.id = p.category_id "
            + "LEFT JOIN brands b ON b.id = p.brand_id ";

    /** Critères de recherche du catalogue. */
    public record Filter(String query, Integer categoryId, Integer brandId,
                         StockStatus status, Boolean active) {

        public static Filter all() {
            return new Filter(null, null, null, null, Boolean.TRUE);
        }
    }

    // --------------------------------------------------------------- lectures

    public Optional<Product> findById(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Product> findByBarcode(String barcode) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.barcode = ?")) {
            ps.setString(1, barcode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Product> findByRef(String ref) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.ref = ?")) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Produits portant le même nom (la collation MySQL ignore la casse et les
     * accents). Sert au contrôle de doublon avant création.
     */
    public List<Product> findByName(String name) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.name = ? ORDER BY p.id")) {
            ps.setString(1, name == null ? "" : name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                List<Product> liste = new ArrayList<>();
                while (rs.next()) {
                    liste.add(map(rs));
                }
                return liste;
            }
        }
    }

    /** Liste paginée selon les filtres, triée sur une colonne autorisée. */
    public List<Product> search(Filter f, String sortKey, boolean asc, int limit, int offset)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, f);
        sql.append("ORDER BY ").append(orderBy(sortKey, asc));
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Product> liste = new ArrayList<>();
                while (rs.next()) {
                    liste.add(map(rs));
                }
                return liste;
            }
        }
    }

    public long count(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, f);
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Produits actifs, pour les listes déroulantes. */
    public List<Product> allActive() throws SQLException {
        return search(Filter.all(), "name", true, 5000, 0);
    }

    /** Produits en alerte : rupture (quantité 0) ou stock ≤ seuil. */
    public List<Product> alerts() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT
                     + "WHERE p.active = 1 AND (p.quantity <= 0 OR p.quantity <= p.alert_threshold) "
                     + "ORDER BY p.quantity ASC, p.name");
             ResultSet rs = ps.executeQuery()) {
            List<Product> liste = new ArrayList<>();
            while (rs.next()) {
                liste.add(map(rs));
            }
            return liste;
        }
    }

    /** Produits sans code-barres (pour la génération en masse). */
    public List<Product> withoutBarcode() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT
                     + "WHERE (p.barcode IS NULL OR p.barcode = '') ORDER BY p.id");
             ResultSet rs = ps.executeQuery()) {
            List<Product> liste = new ArrayList<>();
            while (rs.next()) {
                liste.add(map(rs));
            }
            return liste;
        }
    }

    // ------------------------------------------------------------- statistiques

    /** Valeur du stock au coût (prix d'achat moyen pondéré si renseigné). */
    public BigDecimal stockValueAtCost() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(quantity * CASE WHEN avg_cost > 0 THEN avg_cost "
                             + "ELSE purchase_price END), 0) FROM products WHERE active = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    public long countActive() throws SQLException {
        return scalar("SELECT COUNT(*) FROM products WHERE active = 1");
    }

    public long countOutOfStock() throws SQLException {
        return scalar("SELECT COUNT(*) FROM products WHERE active = 1 AND quantity <= 0");
    }

    public long countLowStock() throws SQLException {
        return scalar("SELECT COUNT(*) FROM products WHERE active = 1 AND quantity > 0 "
                + "AND quantity <= alert_threshold");
    }

    private long scalar(String sql) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // -------------------------------------------------------------- écritures

    public long nextRefNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(MAX(CAST(SUBSTRING(ref, 3) AS UNSIGNED)), 0) + 1 "
                             + "FROM products WHERE ref LIKE 'P-%'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public boolean refExists(String ref) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE ref = ?")) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public boolean barcodeExists(String barcode, int excludeId) throws SQLException {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE barcode = ? AND id <> ?")) {
            ps.setString(1, barcode);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** Insère un produit et retourne son identifiant. */
    public int insert(Product p) throws SQLException {
        String sql = "INSERT INTO products (ref, barcode, name, description, category_id, brand_id, "
                + "unit, purchase_price, avg_cost, sale_price, vat_rate, alert_threshold, quantity, "
                + "track_imei, warranty_months, image_path, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, p.getRef());
            ps.setString(i++, p.getBarcode());
            ps.setString(i++, p.getName());
            ps.setString(i++, p.getDescription());
            ps.setObject(i++, p.getCategoryId());
            ps.setObject(i++, p.getBrandId());
            ps.setString(i++, p.getUnit());
            ps.setBigDecimal(i++, p.getPurchasePrice());
            ps.setBigDecimal(i++, p.getAvgCost());
            ps.setBigDecimal(i++, p.getSalePrice());
            ps.setBigDecimal(i++, p.getVatRate());
            ps.setInt(i++, p.getAlertThreshold());
            ps.setInt(i++, p.getQuantity());
            ps.setInt(i++, p.isTrackImei() ? 1 : 0);
            ps.setInt(i++, p.getWarrantyMonths());
            ps.setString(i++, p.getImagePath());
            ps.setInt(i, p.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public void update(Product p) throws SQLException {
        String sql = "UPDATE products SET ref = ?, barcode = ?, name = ?, description = ?, "
                + "category_id = ?, brand_id = ?, unit = ?, purchase_price = ?, avg_cost = ?, "
                + "sale_price = ?, vat_rate = ?, alert_threshold = ?, track_imei = ?, "
                + "warranty_months = ?, image_path = ?, active = ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, p.getRef());
            ps.setString(i++, p.getBarcode());
            ps.setString(i++, p.getName());
            ps.setString(i++, p.getDescription());
            ps.setObject(i++, p.getCategoryId());
            ps.setObject(i++, p.getBrandId());
            ps.setString(i++, p.getUnit());
            ps.setBigDecimal(i++, p.getPurchasePrice());
            ps.setBigDecimal(i++, p.getAvgCost());
            ps.setBigDecimal(i++, p.getSalePrice());
            ps.setBigDecimal(i++, p.getVatRate());
            ps.setInt(i++, p.getAlertThreshold());
            ps.setInt(i++, p.isTrackImei() ? 1 : 0);
            ps.setInt(i++, p.getWarrantyMonths());
            ps.setString(i++, p.getImagePath());
            ps.setInt(i++, p.isActive() ? 1 : 0);
            ps.setInt(i, p.getId());
            ps.executeUpdate();
        }
    }

    public void setActive(int id, boolean active) throws SQLException {
        execute("UPDATE products SET active = ? WHERE id = ?", active ? 1 : 0, id);
    }

    public void setBarcode(int id, String barcode) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET barcode = ? WHERE id = ?")) {
            ps.setString(1, barcode);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void setImagePath(int id, String path) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET image_path = ? WHERE id = ?")) {
            ps.setString(1, path);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Met à jour le cache de quantité (appelé par le StockService, en transaction). */
    public void setQuantity(int id, int quantity) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET quantity = ? WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Verrouille la ligne produit et renvoie ses données de stock (SELECT ... FOR UPDATE). */
    public Optional<Product> lockForUpdate(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, ref, name, quantity, avg_cost, purchase_price, alert_threshold, "
                             + "track_imei, sale_price FROM products WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Product p = new Product(rs.getInt("id"), rs.getString("ref"));
                p.setName(rs.getString("name"));
                p.setQuantity(rs.getInt("quantity"));
                p.setAvgCost(rs.getBigDecimal("avg_cost"));
                p.setPurchasePrice(rs.getBigDecimal("purchase_price"));
                p.setAlertThreshold(rs.getInt("alert_threshold"));
                p.setTrackImei(rs.getInt("track_imei") == 1);
                p.setSalePrice(rs.getBigDecimal("sale_price"));
                return Optional.of(p);
            }
        }
    }

    private void execute(String sql, Object... params) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    // ----------------------------------------------------------------- interne

    private static void appendWhere(StringBuilder sql, List<Object> params, Filter f) {
        if (f == null) {
            return;
        }
        if (f.active() != null) {
            sql.append("AND p.active = ? ");
            params.add(f.active() ? 1 : 0);
        }
        if (f.query() != null && !f.query().isBlank()) {
            sql.append("AND (p.name LIKE ? OR p.ref LIKE ? OR p.barcode LIKE ?) ");
            String q = "%" + f.query().trim() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (f.categoryId() != null) {
            sql.append("AND p.category_id = ? ");
            params.add(f.categoryId());
        }
        if (f.brandId() != null) {
            sql.append("AND p.brand_id = ? ");
            params.add(f.brandId());
        }
        if (f.status() == StockStatus.OUT) {
            sql.append("AND p.quantity <= 0 ");
        } else if (f.status() == StockStatus.LOW) {
            sql.append("AND p.quantity > 0 AND p.quantity <= p.alert_threshold ");
        } else if (f.status() == StockStatus.OK) {
            sql.append("AND p.quantity > p.alert_threshold ");
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /** Colonne de tri : liste blanche (aucune concaténation de valeur utilisateur). */
    private static String orderBy(String key, boolean asc) {
        String direction = asc ? "ASC" : "DESC";
        String cle = key == null ? "name" : key;
        if ("status".equals(cle)) {
            // Rupture d'abord (ASC), puis stock faible, puis disponible.
            return "(p.quantity <= 0) " + direction + ", (p.quantity <= p.alert_threshold) " + direction;
        }
        String colonne = switch (cle) {
            case "ref" -> "p.ref";
            case "barcode" -> "p.barcode";
            case "category" -> "c.name";
            case "brand" -> "b.name";
            case "purchasePrice" -> "p.purchase_price";
            case "salePrice" -> "p.sale_price";
            case "quantity" -> "p.quantity";
            case "updatedAt" -> "p.updated_at";
            default -> "p.name";
        };
        return colonne + " " + direction;
    }

    private static Product map(ResultSet rs) throws SQLException {
        Product p = new Product(rs.getInt("id"), rs.getString("ref"));
        p.setBarcode(rs.getString("barcode"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        Object categoryId = rs.getObject("category_id");
        p.setCategoryId(categoryId == null ? null : ((Number) categoryId).intValue());
        p.setCategoryName(rs.getString("category_name"));
        Object brandId = rs.getObject("brand_id");
        p.setBrandId(brandId == null ? null : ((Number) brandId).intValue());
        p.setBrandName(rs.getString("brand_name"));
        p.setUnit(rs.getString("unit"));
        p.setPurchasePrice(rs.getBigDecimal("purchase_price"));
        p.setAvgCost(rs.getBigDecimal("avg_cost"));
        p.setSalePrice(rs.getBigDecimal("sale_price"));
        p.setVatRate(rs.getBigDecimal("vat_rate"));
        p.setAlertThreshold(rs.getInt("alert_threshold"));
        p.setQuantity(rs.getInt("quantity"));
        p.setTrackImei(rs.getInt("track_imei") == 1);
        p.setWarrantyMonths(rs.getInt("warranty_months"));
        p.setImagePath(rs.getString("image_path"));
        p.setActive(rs.getInt("active") == 1);
        Timestamp created = rs.getTimestamp("created_at");
        p.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        p.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return p;
    }
}
