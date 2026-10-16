package com.techshop.service;

import com.techshop.dao.ProductDao;
import com.techshop.dao.ProductUnitDao;
import com.techshop.dao.StockMovementDao;
import com.techshop.model.Product;
import com.techshop.model.StockMovement;
import com.techshop.model.StockMovementType;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du StockService (SPEC §6.2) sans base de données : les DAO sont
 * remplacés par des doubles en mémoire. On vérifie la cohérence du cache de
 * quantité, l'interdiction du stock négatif et la génération des ajustements
 * d'inventaire.
 */
class StockServiceTest {

    private FakeProductDao products;
    private FakeMovementDao movements;
    private StockService service;

    @BeforeEach
    void setUp() {
        products = new FakeProductDao();
        movements = new FakeMovementDao();
        service = new StockService(products, movements, new ProductUnitDao(), ActivityAudit.SILENCIEUX);
    }

    private Product produit(int id, int quantite) {
        Product p = new Product(id, "P-" + String.format("%06d", id));
        p.setName("Produit " + id);
        p.setQuantity(quantite);
        p.setPurchasePrice(new java.math.BigDecimal("100.00"));
        p.setSalePrice(new java.math.BigDecimal("150.00"));
        products.put(p);
        return p;
    }

    @Test
    @DisplayName("Entrée puis sortie : la quantité reste cohérente et les mouvements sont tracés")
    void entreePuisSortie() {
        produit(1, 10);

        service.apply(1, StockMovementType.ENTREE, 5, null, null, "Réception", "MANUAL", null);
        assertEquals(15, products.quantityOf(1), "après l'entrée");
        assertEquals(1, movements.list.size());

        service.apply(1, StockMovementType.SORTIE, 3, null, null, "Sortie", "MANUAL", null);
        assertEquals(12, products.quantityOf(1), "après la sortie");
        assertEquals(2, movements.list.size());

        StockMovement premier = movements.list.get(0);
        assertEquals(10, premier.quantityBefore());
        assertEquals(15, premier.quantityAfter());
        assertEquals(5, premier.quantity());

        StockMovement second = movements.list.get(1);
        assertEquals(15, second.quantityBefore());
        assertEquals(12, second.quantityAfter());
        assertEquals(-3, second.quantity(), "la sortie est négative");
    }

    @Test
    @DisplayName("Le stock ne devient jamais négatif : la sortie est refusée")
    void stockJamaisNegatif() {
        produit(1, 2);

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> service.apply(1, StockMovementType.SORTIE, 5, null, null, "Trop", "MANUAL", null));

        assertTrue(erreur.getMessage().toLowerCase().contains("insuffisant"));
        assertEquals(2, products.quantityOf(1), "la quantité est inchangée");
        assertTrue(movements.list.isEmpty(), "aucun mouvement enregistré");
    }

    @Test
    @DisplayName("La vente diminue le stock et porte un signe négatif")
    void venteDiminue() {
        produit(1, 5);
        service.apply(1, StockMovementType.VENTE, 2, null, null, "Vente", "SALE", 42);

        assertEquals(3, products.quantityOf(1));
        StockMovement m = movements.list.get(0);
        assertEquals("VENTE", m.type());
        assertEquals(-2, m.quantity());
        assertEquals(42, m.referenceId());
    }

    @Test
    @DisplayName("L'ajustement porte la nouvelle quantité et exige un motif")
    void ajustement() {
        produit(1, 10);
        service.adjust(1, 7, "Casse en atelier");
        assertEquals(7, products.quantityOf(1));

        StockMovement m = movements.list.get(0);
        assertEquals("AJUSTEMENT", m.type());
        assertEquals(-3, m.quantity());
        assertEquals(7, m.quantityAfter());

        assertThrows(ValidationException.class, () -> service.adjust(1, 8, "  "));
    }

    @Test
    @DisplayName("Un ajustement sans écart ne crée aucun mouvement")
    void ajustementSansEcart() {
        produit(1, 4);
        service.adjust(1, 4, "Contrôle");
        assertEquals(4, products.quantityOf(1));
        assertTrue(movements.list.isEmpty());
    }

    @Test
    @DisplayName("L'inventaire génère un ajustement par écart constaté")
    void inventaireGenereAjustements() {
        Product a = produit(1, 10);
        Product b = produit(2, 3);
        Product c = produit(3, 8);

        List<StockService.InventoryLine> lignes = List.of(
                new StockService.InventoryLine(a, 8),   // -2
                new StockService.InventoryLine(b, 3),   // écart nul
                new StockService.InventoryLine(c, 12)); // +2

        int ajustes = service.applyInventory(lignes, "Inventaire mensuel");

        assertEquals(2, ajustes);
        assertEquals(8, products.quantityOf(1));
        assertEquals(3, products.quantityOf(2));
        assertEquals(12, products.quantityOf(3));
        assertEquals(2, movements.list.size());
        assertEquals("INVENTAIRE", movements.list.get(0).type());
    }

    @Test
    @DisplayName("Un inventaire sans écart ne génère rien et exige un motif")
    void inventaireSansEcart() {
        Product a = produit(1, 10);
        int ajustes = service.applyInventory(
                List.of(new StockService.InventoryLine(a, 10)), "Contrôle");
        assertEquals(0, ajustes);
        assertTrue(movements.list.isEmpty());

        assertThrows(ValidationException.class,
                () -> service.applyInventory(
                        List.of(new StockService.InventoryLine(a, 5)), null));
    }

    // ------------------------------------------------------------- doubles

    private static final class FakeProductDao extends ProductDao {

        private final Map<Integer, Product> base = new HashMap<>();

        void put(Product p) {
            base.put(p.getId(), p);
        }

        int quantityOf(int id) {
            return base.get(id).getQuantity();
        }

        @Override
        public Optional<Product> lockForUpdate(int id) {
            Product p = base.get(id);
            if (p == null) {
                return Optional.empty();
            }
            Product copie = new Product(p.getId(), p.getRef());
            copie.setName(p.getName());
            copie.setQuantity(p.getQuantity());
            copie.setAvgCost(p.getAvgCost());
            copie.setPurchasePrice(p.getPurchasePrice());
            copie.setSalePrice(p.getSalePrice());
            copie.setAlertThreshold(p.getAlertThreshold());
            copie.setBarcode(p.getBarcode());
            return Optional.of(copie);
        }

        @Override
        public void setQuantity(int id, int quantity) throws SQLException {
            Product p = base.get(id);
            if (p != null) {
                p.setQuantity(quantity);
            }
        }
    }

    private static final class FakeMovementDao extends StockMovementDao {

        private final List<StockMovement> list = new ArrayList<>();

        @Override
        public long insert(int productId, Integer productUnitId, String type, int quantity,
                           int quantityBefore, int quantityAfter, java.math.BigDecimal unitCost,
                           String reason, String referenceType, Integer referenceId, Integer userId,
                           LocalDateTime movementDate) {
            long id = list.size() + 1L;
            list.add(new StockMovement(id, productId, null, null, productUnitId, null, type,
                    quantity, quantityBefore, quantityAfter, unitCost, reason, referenceType,
                    referenceId, userId, null, movementDate, null));
            return id;
        }
    }
}
