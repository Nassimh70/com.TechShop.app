package com.techshop.service;

import com.techshop.model.CashMovement;
import com.techshop.model.CashMovementType;
import com.techshop.model.CashSession;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du service de caisse (SPEC §4.10) sans base : ouverture, mouvements,
 * écart de clôture et blocage des ventes sans session ouverte.
 */
class CashServiceTest {

    private FakeDb db;
    private CashService cash;

    @BeforeEach
    void setUp() {
        db = new FakeDb();
        cash = db.cashService();
        SessionContext.get().setUser(new User(1, "vendeur", "Vendeur Test", Role.ADMIN, true));
    }

    @AfterEach
    void tearDown() {
        SessionContext.get().logout();
    }

    @Test
    @DisplayName("Sans session ouverte, l'ouverture est exigée par requireOpen")
    void aucuneSessionOuverte() {
        assertFalse(cash.isOpen());
        ValidationException e = assertThrows(ValidationException.class, cash::requireOpen);
        assertTrue(e.getMessage().toLowerCase().contains("caisse"));
    }

    @Test
    @DisplayName("Ouvrir une caisse fixe le fonds, la date et le numéro ; pas de double ouverture")
    void ouvertureEtDoubleOuverture() {
        CashSession s = cash.openSession(new BigDecimal("5000"), "Ouverture du matin");

        assertTrue(cash.isOpen());
        assertEquals("C-000001", s.getNumber());
        assertEquals(CashSession.OUVERTE, s.getStatus());
        assertEquals(0, new BigDecimal("5000").compareTo(s.getOpeningFloat()));

        assertThrows(ValidationException.class,
                () -> cash.openSession(new BigDecimal("100"), "doublon"));
    }

    @Test
    @DisplayName("L'ouverture refuse un fonds négatif")
    void fondsNegatifRefuse() {
        assertThrows(ValidationException.class,
                () -> cash.openSession(new BigDecimal("-1"), null));
    }

    @Test
    @DisplayName("Une sortie sans motif est refusée ; entrées et sorties alimentent l'attendu")
    void mouvementsEtAttendu() {
        cash.openSession(new BigDecimal("5000"), "matin");

        assertThrows(ValidationException.class, () -> cash
                .addMovement(CashMovementType.SORTIE, new BigDecimal("1000"), "   ", null));
        assertThrows(ValidationException.class, () -> cash
                .addMovement(CashMovementType.ENTREE, BigDecimal.ZERO, "nul", null));

        cash.addMovement(CashMovementType.ENTREE, new BigDecimal("2000"), "apport", null);
        cash.addMovement(CashMovementType.SORTIE, new BigDecimal("500"), "achat", null);

        CashSession s = cash.requireOpen();
        CashService.SessionState st = cash.state(s.getId(), s);
        assertEquals(0, new BigDecimal("6500").compareTo(st.expectedCash()), "5000 + 2000 - 500");
        assertEquals(2, st.movementCount());

        CashMovement sortie = db.cashMovements.stream()
                .filter(m -> CashMovementType.SORTIE.name().equals(m.getType()))
                .findFirst().orElseThrow();
        assertEquals("achat", sortie.getReason());
        assertFalse(sortie.isEntree());
    }

    @Test
    @DisplayName("La clôture calcule l'écart (compté − attendu) et ferme la session")
    void clotureCalculeEcart() {
        CashSession s = cash.openSession(new BigDecimal("5000"), "matin");
        cash.addMovement(CashMovementType.ENTREE, new BigDecimal("3000"), "ventes", null);

        CashSession fermee = cash.closeSession(new BigDecimal("7000"), "soir");

        assertEquals(CashSession.FERMEE, fermee.getStatus());
        assertFalse(fermee.isOpen());
        assertEquals(0, new BigDecimal("8000").compareTo(fermee.getExpectedCash()));
        assertEquals(0, new BigDecimal("7000").compareTo(fermee.getCountedCash()));
        assertEquals(0, new BigDecimal("-1000").compareTo(fermee.getDifference()));
        assertThrows(ValidationException.class, cash::requireOpen);
        assertEquals(0, new BigDecimal("-1000").compareTo(s.getDifference()), "même objet mis à jour");
    }

    @Test
    @DisplayName("L'indicateur de la barre supérieure suit l'état de la caisse")
    void indicateurSuitLEtat() {
        assertFalse(cash.openProperty().get());

        cash.openSession(new BigDecimal("1000"), null);
        assertTrue(cash.openProperty().get());
        assertEquals("C-000001", cash.sessionNumberProperty().get());

        cash.closeSession(new BigDecimal("1000"), null);
        assertFalse(cash.openProperty().get());
        assertEquals("", cash.sessionNumberProperty().get());
    }
}
