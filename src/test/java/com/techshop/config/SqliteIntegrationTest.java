package com.techshop.config;

import com.techshop.dao.ProductDao;
import com.techshop.dao.ReportDao;
import com.techshop.dao.SaleDao;
import com.techshop.dao.SettingDao;
import com.techshop.dao.UserDao;
import com.techshop.model.Product;
import com.techshop.model.Sale;
import com.techshop.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests d'intégration SQLite : création automatique de la base, schéma,
 * clés étrangères, CRUD, relations, requêtes de rapport, persistance,
 * sauvegarde et restauration.
 */
class SqliteIntegrationTest {

    @TempDir
    Path tempDir;

    private Path base;
    private Path sauvegardes;

    @BeforeEach
    void demarrer() {
        base = tempDir.resolve("data").resolve("techshop.db");
        sauvegardes = tempDir.resolve("backups");
        System.setProperty("techshop.db.path", base.toString());
        System.setProperty("techshop.backup.dir", sauvegardes.toString());
        System.setProperty("techshop.profile", "prod");
        DatabaseManager.shutdown();
        DatabaseManager.init();
        if (!DatabaseManager.isReady()) {
            fail("Initialisation SQLite impossible : " + DatabaseManager.getLastError());
        }
    }

    @AfterEach
    void arreter() {
        DatabaseManager.shutdown();
        System.clearProperty("techshop.db.path");
        System.clearProperty("techshop.backup.dir");
        System.clearProperty("techshop.profile");
    }

    @Test
    void premierDemarrageCreeLaBaseEtLeSchema() throws Exception {
        assertTrue(java.nio.file.Files.isRegularFile(base), "Le fichier SQLite doit être créé");
        try (Connection c = DatabaseManager.connection();
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys doit être actif");
            }
            for (String table : new String[]{"users", "products", "customers", "sales",
                    "sale_items", "sale_payments", "repair_tickets", "cash_sessions",
                    "goods_receipts", "goods_receipt_items", "purchase_orders",
                    "supplier_payments", "sale_returns", "sale_return_items",
                    "stock_movements", "settings", "schema_version"}) {
                assertTrue(tableExiste(c, table), "Table absente : " + table);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "V001 et V002 doivent être appliquées en prod");
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Le profil prod ne doit contenir aucun produit de démo");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM settings WHERE cle = 'ticket.footer'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Les données de référence doivent être présentes");
            }
        }
    }

    @Test
    void crudRelationsEtRequetesDeRapport() throws Exception {
        UserDao users = new UserDao();
        ProductDao products = new ProductDao();
        SaleDao sales = new SaleDao();
        ReportDao reports = new ReportDao();

        int userId = users.insert("gerant", "$2a$10$hash", "Gérant Test",
                "0555000000", "gerant@example.test", true, false);
        assertTrue(userId > 0);

        Product p = new Product();
        p.setRef("P-000001");
        p.setName("Téléphone test");
        p.setBarcode("1234567890123");
        p.setPurchasePrice(new BigDecimal("100.00"));
        p.setAvgCost(new BigDecimal("100.00"));
        p.setSalePrice(new BigDecimal("150.00"));
        p.setQuantity(10);
        p.setUnit("piece");
        int productId = products.insert(p);
        assertTrue(productId > 0);

        int customerId;
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO customers (code, full_name, phone) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "C-000001");
            ps.setString(2, "Client Test");
            ps.setString(3, "0666000000");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                customerId = keys.getInt(1);
            }
        }

        Sale sale = new Sale();
        sale.setCustomerId(customerId);
        sale.setUserId(userId);
        sale.setSaleDate(LocalDateTime.now());
        sale.setSubtotal(new BigDecimal("300.00"));
        sale.setTotal(new BigDecimal("300.00"));
        sale.setPaidAmount(new BigDecimal("300.00"));
        sale.setDueAmount(BigDecimal.ZERO);
        sale.setProfit(new BigDecimal("100.00"));
        int saleId = sales.insert(sale);
        assertTrue(saleId > 0);
        assertEquals("V-000001", sale.getNumber());
        assertEquals(2, sales.nextNumber());

        try (Connection c = DatabaseManager.connection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sale_items (sale_id, product_id, description, quantity, "
                            + "unit_price, unit_cost, total) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, saleId);
                ps.setInt(2, productId);
                ps.setString(3, "Téléphone test");
                ps.setInt(4, 2);
                ps.setBigDecimal(5, new BigDecimal("150.00"));
                ps.setBigDecimal(6, new BigDecimal("100.00"));
                ps.setBigDecimal(7, new BigDecimal("300.00"));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sale_payments (sale_id, method, amount, user_id) "
                            + "VALUES (?, ?, ?, ?)")) {
                for (String methode : new String[]{"ESPECES", "CARTE"}) {
                    ps.setInt(1, saleId);
                    ps.setString(2, methode);
                    ps.setBigDecimal(3, new BigDecimal("150.00"));
                    ps.setInt(4, userId);
                    ps.executeUpdate();
                }
            }
        }

        var relue = sales.byId(saleId);
        assertTrue(relue.isPresent());
        assertEquals(1, relue.get().getItemCount());
        assertNotNull(relue.get().getMethods());
        assertTrue(relue.get().getMethods().contains("CARTE"));
        assertTrue(relue.get().getMethods().contains("ESPECES"));

        LocalDate aujourdhui = LocalDate.now();
        var top = reports.topProducts(aujourdhui, aujourdhui);
        assertEquals(1, top.size());
        assertEquals("Téléphone test", top.get(0).name());
        assertEquals(2, top.get(0).qty());
        assertEquals(0, new BigDecimal("300.00").compareTo(top.get(0).total()));

        var finances = reports.financesDaily(aujourdhui, aujourdhui);
        assertFalse(finances.isEmpty());
        assertEquals(0, new BigDecimal("300.00").compareTo(finances.get(0).in()));

        // Contrainte de clé étrangère : un produit inexistant doit être refusé.
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, total) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, saleId);
            ps.setInt(2, 999_999);
            ps.setInt(3, 1);
            ps.setBigDecimal(4, BigDecimal.ONE);
            ps.setBigDecimal(5, BigDecimal.ONE);
            assertThrows(SQLException.class, ps::executeUpdate);
        }
    }

    @Test
    void persistanceEtPasDeReinitialisation() throws Exception {
        new SettingDao().set("test.marqueur", "conserve");
        DatabaseManager.shutdown();
        DatabaseManager.init();
        assertTrue(DatabaseManager.isReady(), DatabaseManager.getLastError());
        assertEquals("conserve", new SettingDao().get("test.marqueur").orElse(null));
    }

    @Test
    void sauvegardeEtRestauration() throws Exception {
        SettingDao settings = new SettingDao();
        settings.set("test.marqueur", "avant-sauvegarde");

        Path fichier = BackupService.get().backupNow(sauvegardes);
        assertTrue(java.nio.file.Files.isRegularFile(fichier));
        assertTrue(java.nio.file.Files.size(fichier) > 0);

        settings.set("test.marqueur", "apres-sauvegarde");
        assertEquals("apres-sauvegarde", settings.get("test.marqueur").orElse(null));

        BackupService.RestoreResult resultat = BackupService.get().restore(fichier, sauvegardes);
        assertNotNull(resultat.safetyBackup());
        assertTrue(java.nio.file.Files.isRegularFile(resultat.safetyBackup()));
        assertTrue(DatabaseManager.isReady(), DatabaseManager.getLastError());
        assertEquals("avant-sauvegarde", settings.get("test.marqueur").orElse(null));
    }

    private static boolean tableExiste(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
