package com.techshop.service;

import com.techshop.model.CashMovementType;
import com.techshop.model.PaymentMethod;
import com.techshop.model.Product;
import com.techshop.model.ProductUnit;
import com.techshop.model.Role;
import com.techshop.model.Sale;
import com.techshop.model.SaleItem;
import com.techshop.model.SaleReturn;
import com.techshop.model.User;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du service de vente (SPEC §4.3, §4.4) sans base : encaissement complet,
 * rendu de monnaie, refus (caisse fermée, stock insuffisant, IMEI déjà vendu,
 * crédit sans client), puis retour / remboursement.
 */
class SaleServiceTest {

    private FakeDb db;
    private CashService cash;
    private SaleService ventes;

    @BeforeEach
    void setUp() {
        db = new FakeDb();
        cash = db.cashService();
        ventes = new SaleService(db.saleDao, db.itemDao, db.paymentDao, db.returnDao,
                db.productDao, db.unitDao, db.customerDao, cash, db.stockService(), db.journal);
        SessionContext.get().setUser(new User(1, "vendeur", "Vendeur Test", Role.ADMIN, true));
        cash.openSession(new BigDecimal("10000"), "test");
    }

    @AfterEach
    void tearDown() {
        SessionContext.get().logout();
    }

    private Product produit(int id, int quantite, String prix) {
        Product p = new Product(id, "P-" + String.format("%06d", id));
        p.setName("Produit " + id);
        p.setQuantity(quantite);
        p.setPurchasePrice(new BigDecimal("1000.00"));
        p.setAvgCost(new BigDecimal("1000.00"));
        p.setSalePrice(new BigDecimal(prix));
        p.setVatRate(BigDecimal.ZERO);
        db.products.put(id, p);
        return p;
    }

    private SaleService.CheckoutLine ligne(Product p, int qte, Integer unitId) {
        return new SaleService.CheckoutLine(p, qte, unitId, null, p.getSalePrice(),
                BigDecimal.ZERO, 0);
    }

    private SaleService.PaymentLine especes(String montant) {
        return new SaleService.PaymentLine(PaymentMethod.ESPECES.name(), new BigDecimal(montant));
    }

    // ------------------------------------------------------------ encaissement

    @Test
    @DisplayName("Vente comptant : stock diminué, règlement écrit, caisse alimentée")
    void venteEspecesComplete() {
        Product p = produit(1, 5, "2000.00");

        SaleService.CheckoutResult res = ventes.checkout(new SaleService.CheckoutRequest(
                null, List.of(ligne(p, 2, null)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine(PaymentMethod.ESPECES.name(),
                        new BigDecimal("4000"))), null));

        Sale sale = res.sale();
        assertEquals(Sale.PAYEE, sale.getPaymentStatus());
        assertEquals(0, new BigDecimal("4000").compareTo(sale.getTotal()));
        assertEquals(0, new BigDecimal("4000").compareTo(sale.getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(res.change()));
        assertTrue(sale.getNumber().startsWith("V-"));
        assertEquals(3, db.quantityOf(1), "5 - 2 = 3");
        assertEquals(1, res.sale().getItems().size(), "la vente retournée porte ses lignes (ticket)");
        assertEquals(2, res.sale().getItems().get(0).getQuantity());
        assertEquals(1, db.salePayments.size());
        assertEquals(PaymentMethod.ESPECES.name(), db.salePayments.get(0).getMethod());
        assertEquals(1, db.cashMovements.size());
        assertEquals(CashMovementType.ENTREE.name(), db.cashMovements.get(0).getType());
        assertEquals(0, new BigDecimal("4000.00")
                .compareTo(db.cashMovements.get(0).getAmount()));
    }

    @Test
    @DisplayName("Un paiement supérieur au total rend la monnaie sans gonfler le payé")
    void renduMonnaie() {
        Product p = produit(1, 5, "2000");
        var res = ventes.checkout(new SaleService.CheckoutRequest(null,
                List.of(ligne(p, 2, null)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("5000"))), null));

        assertEquals(0, new BigDecimal("4000").compareTo(res.sale().getPaidAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(res.change()));
        assertEquals(Sale.PAYEE, res.sale().getPaymentStatus());
    }

    @Test
    @DisplayName("Remise de ligne et remise globale réduisent le total")
    void remisesReduisentLeTotal() {
        Product p = produit(1, 5, "2000");
        var ligne = new SaleService.CheckoutLine(p, 3, null, null, p.getSalePrice(),
                new BigDecimal("500"), 0);
        var res = ventes.checkout(new SaleService.CheckoutRequest(null, List.of(ligne),
                new BigDecimal("1000"), List.of(new SaleService.PaymentLine("ESPECES",
                        new BigDecimal("4500"))), null));

        assertEquals(0, new BigDecimal("4500").compareTo(res.sale().getTotal()), "6000 - 500 - 1000");
        assertEquals(0, new BigDecimal("1500").compareTo(res.sale().getDiscount()));
        assertEquals(Sale.PAYEE, res.sale().getPaymentStatus());
    }

    @Test
    @DisplayName("Aucune vente possible sans session de caisse ouverte")
    void venteRefuseeCaisseFermee() {
        cash.closeSession(new BigDecimal("10000"), "fin");
        Product p = produit(1, 5, "2000");

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> ventes.checkout(new SaleService.CheckoutRequest(null,
                        List.of(ligne(p, 1, null)), BigDecimal.ZERO,
                        List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("2000"))),
                        null)));

        assertTrue(erreur.getMessage().toLowerCase().contains("caisse"));
        assertEquals(5, db.quantityOf(1), "le stock reste intact");
        assertTrue(db.sales.isEmpty(), "aucune vente enregistrée");
    }

    @Test
    @DisplayName("Une vente avec reste à payer exige un client")
    void creditSansClientRefuse() {
        Product p = produit(1, 5, "2000");

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> ventes.checkout(new SaleService.CheckoutRequest(null,
                        List.of(ligne(p, 2, null)), BigDecimal.ZERO,
                        List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("1000"))),
                        null)));

        assertTrue(erreur.getMessage().toLowerCase().contains("client"));
        assertEquals(5, db.quantityOf(1));
    }

    @Test
    @DisplayName("Crédit partiel : la dette est portée au compte du client")
    void creditAvecClient() {
        Product p = produit(1, 5, "2000");
        var res = ventes.checkout(new SaleService.CheckoutRequest(7,
                List.of(ligne(p, 2, null)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("1000"))), null));

        assertEquals(Sale.PARTIELLE, res.sale().getPaymentStatus());
        assertEquals(0, new BigDecimal("4000").compareTo(res.sale().getTotal()));
        assertEquals(0, new BigDecimal("3000").compareTo(res.sale().getDueAmount()));
        assertEquals(0, new BigDecimal("3000").compareTo(db.balances.get(7)));
    }

    @Test
    @DisplayName("Vente entièrement à crédit : statut impayé et dette complète")
    void venteImpayee() {
        Product p = produit(1, 5, "2000");
        var res = ventes.checkout(new SaleService.CheckoutRequest(7,
                List.of(ligne(p, 1, null)), BigDecimal.ZERO, List.of(), null));

        assertEquals(Sale.IMPAYEE, res.sale().getPaymentStatus());
        assertEquals(0, new BigDecimal("2000").compareTo(res.sale().getDueAmount()));
        assertEquals(0, new BigDecimal("2000").compareTo(db.balances.get(7)));
    }

    @Test
    @DisplayName("Stock insuffisant : la vente est refusée et le stock reste intact")
    void stockInsuffisantRefuse() {
        Product p = produit(1, 2, "2000");

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> ventes.checkout(new SaleService.CheckoutRequest(null,
                        List.of(ligne(p, 5, null)), BigDecimal.ZERO,
                        List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("10000"))),
                        null)));

        assertTrue(erreur.getMessage().toLowerCase().contains("insuffisant"));
        assertEquals(2, db.quantityOf(1));
    }

    @Test
    @DisplayName("Produit suivi par IMEI : IMEI obligatoire puis marqué vendu")
    void imeiObligatoireEtMarqueVendu() {
        Product p = produit(1, 1, "50000");
        p.setTrackImei(true);
        db.units.put(10L, new ProductUnit(10L, 1, p.getName(), "123456789012345", null,
                ProductUnit.EN_STOCK, new BigDecimal("40000"), null, LocalDateTime.now(), null));

        assertThrows(ValidationException.class, () -> ventes.checkout(
                new SaleService.CheckoutRequest(null, List.of(ligne(p, 1, null)), BigDecimal.ZERO,
                        List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("50000"))),
                        null)));

        var res = ventes.checkout(new SaleService.CheckoutRequest(null,
                List.of(ligne(p, 1, 10)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("50000"))), null));

        assertEquals(Sale.PAYEE, res.sale().getPaymentStatus());
        assertEquals(ProductUnit.VENDU, db.units.get(10L).status());
        assertEquals(0, db.quantityOf(1), "l'appareil vendu sort du stock");
    }

    @Test
    @DisplayName("Un IMEI déjà vendu ne peut pas être revendu")
    void imeiDejaVenduRefuse() {
        Product p = produit(1, 2, "50000");
        p.setTrackImei(true);
        db.units.put(10L, new ProductUnit(10L, 1, p.getName(), "111111111111111", null,
                ProductUnit.VENDU, new BigDecimal("40000"), 1, LocalDateTime.now(), LocalDateTime.now()));

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> ventes.checkout(new SaleService.CheckoutRequest(null,
                        List.of(ligne(p, 1, 10)), BigDecimal.ZERO,
                        List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("50000"))),
                        null)));

        assertTrue(erreur.getMessage().toLowerCase().contains("vendu")
                || erreur.getMessage().toLowerCase().contains("indisponible"));
        assertEquals(2, db.quantityOf(1));
    }

    // ---------------------------------------------------------------- retour

    @Test
    @DisplayName("Retour total : stock remis, vente remboursée, espèces sorties de caisse")
    void retourTotal() {
        Product p = produit(1, 5, "2000");
        var res = ventes.checkout(new SaleService.CheckoutRequest(null,
                List.of(ligne(p, 2, null)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("4000"))), null));
        int saleId = res.sale().getId();
        SaleItem item = db.saleItems.get(0);

        SaleReturn retour = ventes.refund(saleId,
                List.of(new SaleService.RefundLine(item.getId(), 2)), "erreur de saisie",
                PaymentMethod.ESPECES.name());

        assertTrue(retour.getNumber().startsWith("RT-"));
        assertEquals(0, new BigDecimal("4000").compareTo(retour.getTotal()));
        assertEquals(5, db.quantityOf(1), "le stock revient à 5");
        assertEquals(Sale.REMBOURSEE, db.sales.get(saleId).getPaymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(db.sales.get(saleId).getDueAmount()));
        assertTrue(db.cashMovements.stream()
                        .anyMatch(m -> CashMovementType.SORTIE.name().equals(m.getType())
                                && new BigDecimal("4000.00").compareTo(m.getAmount()) == 0),
                "un remboursement espèces est enregistré en sortie");
    }

    @Test
    @DisplayName("Retour partiel d'une vente à crédit : la dette du client diminue d'abord")
    void retourPartielReduitLaDette() {
        Product p = produit(1, 5, "2000");
        var res = ventes.checkout(new SaleService.CheckoutRequest(7,
                List.of(ligne(p, 2, null)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("1000"))), null));
        int saleId = res.sale().getId();
        SaleItem item = db.saleItems.get(0);
        assertEquals(0, new BigDecimal("3000").compareTo(db.balances.get(7)));

        ventes.refund(saleId, List.of(new SaleService.RefundLine(item.getId(), 1)),
                "article défectueux", PaymentMethod.ESPECES.name());

        // 3000 de dette − 2000 retournés = 1000 de dette restante
        assertEquals(0, new BigDecimal("1000").compareTo(db.balances.get(7)));
        assertEquals(0, new BigDecimal("1000").compareTo(db.sales.get(saleId).getDueAmount()));
        assertEquals(Sale.PARTIELLE, db.sales.get(saleId).getPaymentStatus());
    }

    @Test
    @DisplayName("Un retour exige un motif et une quantité valide")
    void retourValideLesEntrees() {
        Product p = produit(1, 5, "2000");
        var res = ventes.checkout(new SaleService.CheckoutRequest(null,
                List.of(ligne(p, 1, null)), BigDecimal.ZERO,
                List.of(new SaleService.PaymentLine("ESPECES", new BigDecimal("2000"))), null));
        int saleId = res.sale().getId();
        SaleItem item = db.saleItems.get(0);

        assertThrows(ValidationException.class, () -> ventes.refund(saleId,
                List.of(new SaleService.RefundLine(item.getId(), 1)), "  ", "ESPECES"));
        assertThrows(ValidationException.class, () -> ventes.refund(saleId,
                List.of(new SaleService.RefundLine(item.getId(), 5)), "trop", "ESPECES"));
        assertThrows(ValidationException.class, () -> ventes.refund(saleId,
                List.of(), "vide", "ESPECES"));
    }
}
