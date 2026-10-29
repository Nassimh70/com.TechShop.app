package com.techshop.service;

import com.techshop.dao.ProductDao;
import com.techshop.model.Product;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la détection de doublons à la création d'un produit (SPEC §4.5) :
 * un produit ayant le même nom, la même description et la même marque est
 * refusé ; une description ou une marque différente autorise la création.
 */
class ProductServiceTest {

    private FakeProductDao dao;
    private ProductService service;

    @BeforeEach
    void setUp() {
        SessionContext.get().logout();
        dao = new FakeProductDao();
        service = new ProductService(dao, ActivityAudit.SILENCIEUX, null);
    }

    @Test
    @DisplayName("Toutes les informations identiques : le produit est un doublon")
    void produitIdentiqueEstUnDoublon() {
        assertTrue(ProductService.memeProduit(produit("Écran 24 pouces"), produit("Ecran  24 pouces")));
    }

    @Test
    @DisplayName("Une description différente autorise la création")
    void descriptionDifferenteAutoriseLaCreation() {
        assertFalse(ProductService.memeProduit(produit("Écran 24 pouces"), produit("Écran 27 pouces")));
    }

    @Test
    @DisplayName("Une marque différente autorise la création")
    void marqueDifferenteAutoriseLaCreation() {
        Product autreMarque = produit("Écran 24 pouces");
        autreMarque.setBrandId(3);

        assertFalse(ProductService.memeProduit(produit("Écran 24 pouces"), autreMarque));
    }

    @Test
    @DisplayName("Catégorie, prix, unité ou garantie différents ne créent pas un nouveau produit")
    void informationsDeGestionDifferentesRestentUnDoublon() {
        Product autre = produit("Écran 24 pouces");
        autre.setCategoryId(2);
        autre.setUnit("boîte");
        autre.setWarrantyMonths(24);
        autre.setTrackImei(true);
        autre.setSalePrice(new BigDecimal("175.00"));
        autre.setPurchasePrice(new BigDecimal("90.00"));
        autre.setAlertThreshold(10);

        assertTrue(ProductService.memeProduit(produit("Écran 24 pouces"), autre));
    }

    @Test
    @DisplayName("La création est refusée avec la référence du produit existant")
    void creationRefuseeSiProduitExisteDeja() {
        Product existant = produit("Écran 24 pouces");
        existant.setRef("P-000042");
        dao.existants.add(existant);

        Product nouveau = produit("Écran 24 pouces");
        nouveau.setCategoryId(2);
        nouveau.setSalePrice(new BigDecimal("175.00"));
        nouveau.setWarrantyMonths(6);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.create(nouveau));

        assertEquals("name", ex.getChamp());
        assertTrue(ex.getMessage().contains("P-000042"), ex.getMessage());
        assertTrue(dao.inseres.isEmpty(), "aucune insertion ne doit avoir lieu");
    }

    @Test
    @DisplayName("Un produit dont une information diffère est bien créé")
    void creationAutoriseeSiUneInformationDiffere() {
        dao.existants.add(produit("Écran 24 pouces"));

        Product nouveau = produit("Écran 24 pouces, dalle IPS");
        int id = service.create(nouveau);

        assertEquals(1, id);
        assertEquals(1, dao.inseres.size());
    }

    private static Product produit(String description) {
        Product p = new Product();
        p.setName("Écran Samsung");
        p.setDescription(description);
        p.setCategoryId(1);
        p.setBrandId(2);
        p.setUnit("piece");
        p.setPurchasePrice(new BigDecimal("100.00"));
        p.setSalePrice(new BigDecimal("150.00"));
        p.setVatRate(new BigDecimal("19.00"));
        p.setAlertThreshold(5);
        p.setWarrantyMonths(12);
        p.setTrackImei(false);
        return p;
    }

    /** Double en mémoire : aucune connexion MySQL n'est ouverte. */
    private static final class FakeProductDao extends ProductDao {

        private final List<Product> existants = new ArrayList<>();
        private final List<Product> inseres = new ArrayList<>();
        private int nextId = 1;

        @Override
        public List<Product> findByName(String name) {
            return new ArrayList<>(existants);
        }

        @Override
        public long nextRefNumber() {
            return inseres.size() + 1L;
        }

        @Override
        public boolean barcodeExists(String barcode, int excludeId) {
            return false;
        }

        @Override
        public int insert(Product p) {
            int id = nextId++;
            p.setId(id);
            inseres.add(p);
            return id;
        }

        @Override
        public void setBarcode(int id, String barcode) {
            // Le code-barres généré n'a pas d'incidence sur le test.
        }
    }
}
