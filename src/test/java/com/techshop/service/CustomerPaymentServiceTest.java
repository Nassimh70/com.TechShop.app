package com.techshop.service;

import com.techshop.model.CashMovementType;
import com.techshop.model.Customer;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests des règlements de dettes clients (SPEC §4.7) sans base : diminution du
 * solde, encaissement partiel, refus d'un montant supérieur à la dette, et
 * correction de la date d'un règlement.
 */
class CustomerPaymentServiceTest {

    private FakeDb db;
    private CashService cash;
    private CustomerPaymentService service;

    @BeforeEach
    void setUp() {
        db = new FakeDb();
        cash = db.cashService();
        service = new CustomerPaymentService(db.customerDao, db.customerPaymentDao, cash,
                db.journal);
        SessionContext.get().setUser(new User(1, "vendeur", "Vendeur Test", Role.ADMIN, true));
        cash.openSession(new BigDecimal("10000"), "test");
    }

    @AfterEach
    void tearDown() {
        SessionContext.get().logout();
    }

    private Customer client(int id, String dette) {
        Customer c = new Customer();
        c.setId(id);
        c.setCode("C-" + String.format("%06d", id));
        c.setFullName("Client " + id);
        c.setBalance(new BigDecimal(dette));
        db.clients.put(id, c);
        db.balances.put(id, new BigDecimal(dette));
        return c;
    }

    @Test
    @DisplayName("Règlement partiel : dette diminuée, paiement daté et caisse alimentée")
    void reglementPartiel() {
        client(1, "3000.00");

        var p = service.encaisser(1, new BigDecimal("500"),
                LocalDateTime.now(), "ESPECES", "versement");

        assertEquals(0, new BigDecimal("2500.00").compareTo(db.balances.get(1)));
        assertEquals(1, db.customerPayments.size());
        assertEquals(0, new BigDecimal("500.00").compareTo(p.getAmount()));
        assertNotNull(p.getCashSessionId(), "règlement du jour lié à la caisse ouverte");
        assertEquals(1, db.cashMovements.size());
        assertEquals(CashMovementType.ENTREE.name(), db.cashMovements.get(0).getType());
        assertEquals(0, new BigDecimal("500.00")
                .compareTo(db.cashMovements.get(0).getAmount()));
    }

    @Test
    @DisplayName("Deux règlements partiels diminuent la dette progressivement")
    void plusieursReglementsPartiels() {
        Customer c = client(5, "3000.00");

        service.encaisser(c.getId(), new BigDecimal("500"),
                LocalDateTime.now(), "ESPECES", null);
        assertEquals(0, new BigDecimal("2500.00").compareTo(db.balances.get(5)));

        service.encaisser(c.getId(), new BigDecimal("500"),
                LocalDateTime.now(), "ESPECES", null);
        assertEquals(0, new BigDecimal("2000.00").compareTo(db.balances.get(5)));
        assertEquals(2, db.customerPayments.size());
    }

    @Test
    @DisplayName("Un montant supérieur à la dette est refusé sans rien enregistrer")
    void montantSuperieurRefuse() {
        client(2, "1500.00");

        assertThrows(ValidationException.class, () -> service.encaisser(2,
                new BigDecimal("2000"), LocalDateTime.now(), "ESPECES", null));

        assertEquals(0, new BigDecimal("1500.00").compareTo(db.balances.get(2)));
        assertEquals(0, db.customerPayments.size());
        assertEquals(0, db.cashMovements.size());
    }

    @Test
    @DisplayName("Un règlement antidaté corrige la dette sans toucher à la caisse du jour")
    void reglementAntidateSansCaisse() {
        client(3, "4000.00");

        var p = service.encaisser(3, new BigDecimal("1000"),
                LocalDate.now().minusDays(3).atTime(10, 0), "ESPECES", null);

        assertEquals(0, new BigDecimal("3000.00").compareTo(db.balances.get(3)));
        assertNull(p.getCashSessionId());
        assertEquals(0, db.cashMovements.size());
    }

    @Test
    @DisplayName("La date d'un règlement existant peut être corrigée")
    void correctionDate() {
        client(4, "1000.00");
        var p = service.encaisser(4, new BigDecimal("1000"),
                LocalDateTime.now(), "CARTE", null);

        LocalDateTime nouvelle = LocalDate.now().minusDays(1).atTime(16, 30);
        service.modifierDate(p.getId(), nouvelle, "date corrigée");

        var relu = db.customerPayments.get(0);
        assertEquals(nouvelle, relu.getPaymentDate());
        assertEquals("date corrigée", relu.getNote());
        assertEquals(0, BigDecimal.ZERO.compareTo(db.balances.get(4)), "le solde ne change pas");
    }
}
