package com.techshop.service;

import com.techshop.dao.NotificationLogDao;
import com.techshop.dao.RepairAttachmentDao;
import com.techshop.dao.RepairPartDao;
import com.techshop.dao.RepairPaymentDao;
import com.techshop.dao.RepairStatusHistoryDao;
import com.techshop.dao.RepairTicketDao;
import com.techshop.model.NotificationLog;
import com.techshop.model.PaymentMethod;
import com.techshop.model.Product;
import com.techshop.model.RepairAttachment;
import com.techshop.model.RepairPart;
import com.techshop.model.RepairPayment;
import com.techshop.model.RepairStatus;
import com.techshop.model.RepairStatusHistory;
import com.techshop.model.RepairTicket;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.util.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du service après-vente (SPEC §4.9) sans base de données : les DAO de
 * réparation sont remplacés par des doubles en mémoire et la caisse / le stock
 * réutilisent la maquette {@link FakeDb}. On vérifie le dépôt (numéro, acompte),
 * les transitions de statut contrôlées, la consommation / restitution des pièces,
 * le calcul des totaux, la remise (espèces ou dette) et la journalisation des
 * notifications client.
 */
class RepairServiceTest {

    private FakeDb db;
    private FakeRepairDao tickets;
    private FakeHistoryDao history;
    private FakePartDao parts;
    private FakePaymentDao payments;
    private FakeAttachmentDao attachments;
    private FakeNotificationDao notifications;
    private RepairService sav;

    @BeforeEach
    void setUp() {
        db = new FakeDb();
        tickets = new FakeRepairDao();
        history = new FakeHistoryDao();
        parts = new FakePartDao();
        payments = new FakePaymentDao();
        attachments = new FakeAttachmentDao();
        notifications = new FakeNotificationDao();
        sav = new RepairService(tickets, history, parts, payments, attachments, notifications,
                db.productDao, db.customerDao, db.stockService(), db.cashService(), db.journal);
        SessionContext.get().setUser(new User(1, "admin", "Admin Test", Role.ADMIN, true));
        db.cashService().openSession(new BigDecimal("10000"), "test");
    }

    @AfterEach
    void tearDown() {
        SessionContext.get().logout();
    }

    // ------------------------------------------------------------- fabriques

    private RepairService.DepositRequest depot(String panne, String methode, String acompte) {
        return new RepairService.DepositRequest(1, "Smartphone", "Samsung", "A54", "356789012345678",
                "1234", "Chargeur", "Rayé", panne, "", new BigDecimal("15000"), methode,
                acompte == null ? null : new BigDecimal(acompte),
                LocalDate.now().plusDays(3), 2, 90, "Note interne");
    }

    private Product piece(int id, int quantite) {
        Product p = new Product(id, "P-" + String.format("%06d", id));
        p.setName("Écran " + id);
        p.setQuantity(quantite);
        p.setPurchasePrice(new BigDecimal("3000.00"));
        p.setAvgCost(new BigDecimal("3000.00"));
        p.setSalePrice(new BigDecimal("5000.00"));
        db.products.put(id, p);
        return p;
    }

    // ------------------------------------------------------------------ dépôt

    @Test
    @DisplayName("Dépôt : dossier « Reçu » numéroté, chronologie et acompte espèces en caisse")
    void depotOuvreUnDossierEtEnregistreLAcompte() {
        RepairTicket t = sav.create(depot("Écran cassé", "ESPECES", "2000"));

        assertTrue(t.getNumber().startsWith("R-"));
        assertEquals(RepairStatus.RECU, t.getStatus());
        assertEquals(1, t.getPayments().size());
        assertEquals(0, new BigDecimal("2000.00").compareTo(t.getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.getDueAmount()), "aucun total : rien n'est dû");
        assertEquals(1, t.getHistory().size());
        assertEquals("Dépôt de l'appareil", t.getHistory().get(0).comment());
        assertEquals(1, db.cashMovements.size(), "l'acompte espèces alimente la caisse");
    }

    @Test
    @DisplayName("Dépôt : client et panne déclarée obligatoires")
    void depotExigeClientEtPanne() {
        assertThrows(ValidationException.class, () -> sav.create(new RepairService.DepositRequest(
                null, "Smartphone", null, null, null, null, null, null, "Panne", null, null,
                null, null, null, null, 0, null)));
        assertThrows(ValidationException.class, () -> sav.create(new RepairService.DepositRequest(
                1, "Smartphone", null, null, null, null, null, null, "   ", null, null,
                null, null, null, null, 0, null)));
        assertTrue(tickets.store.isEmpty(), "aucun dossier créé");
    }

    // ------------------------------------------------------------- transitions

    @Test
    @DisplayName("Statut : seules les transitions autorisées passent, « Livrée » interdite ici")
    void changeStatusRespecteLesTransitions() {
        RepairTicket t = sav.create(depot("Ne s'allume plus", null, null));

        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "Prise en charge");
        assertEquals(RepairStatus.DIAGNOSTIC, sav.byId(t.getId()).getStatus());
        assertEquals(2, sav.history(t.getId()).size(), "dépôt + 1 changement");

        assertThrows(ValidationException.class,
                () -> sav.changeStatus(t.getId(), RepairStatus.PRETE, "trop vite"));
        assertThrows(ValidationException.class,
                () -> sav.changeStatus(t.getId(), RepairStatus.LIVREE, "à faire depuis la remise"));
    }

    // ----------------------------------------------------------------- pièces

    @Test
    @DisplayName("Pièce : sortie de stock, total recalculé, puis restitution au retrait")
    void piecesSortentEtReintegrentLeStock() {
        piece(1, 10);
        RepairTicket t = sav.create(depot("Écran fissuré", null, null));
        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "ok");

        RepairTicket apres = sav.addPart(t.getId(), 1, 2, new BigDecimal("4500"), null);
        assertEquals(8, db.quantityOf(1), "2 écrans sortis du stock");
        assertEquals(0, new BigDecimal("9000.00").compareTo(apres.getTotal()));
        assertEquals(1, apres.getParts().size());

        int partId = apres.getParts().get(0).id();
        RepairTicket sansPiece = sav.removePart(partId, t.getId());
        assertEquals(10, db.quantityOf(1), "la pièce retirée revient au stock");
        assertEquals(0, BigDecimal.ZERO.compareTo(sansPiece.getTotal()));
        assertTrue(sansPiece.getParts().isEmpty());
    }

    @Test
    @DisplayName("Pièce : stock insuffisant refusé, dossier clôturé non modifiable")
    void pieceRefuseeSiStockInsuffisant() {
        piece(1, 1);
        RepairTicket t = sav.create(depot("Batterie", null, null));
        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "ok");

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> sav.addPart(t.getId(), 1, 3, null, null));
        assertTrue(erreur.getMessage().toLowerCase().contains("insuffisant"));
        assertEquals(1, db.quantityOf(1));

        sav.changeStatus(t.getId(), RepairStatus.ANNULEE, "refus du devis");
        assertThrows(ValidationException.class, () -> sav.addPart(t.getId(), 1, 1, null, null));
    }

    // ----------------------------------------------------------------- totaux

    @Test
    @DisplayName("Main d'œuvre, remise et acompte : total, payé et reste dû cohérents")
    void mainDoeuvreEtAcomptesMetierAJourLesTotaux() {
        RepairTicket t = sav.create(depot("Batterie", null, null));
        sav.updateLabor(t.getId(), new BigDecimal("3000"), new BigDecimal("500"));
        RepairTicket apres = sav.addDeposit(t.getId(), "ESPECES", new BigDecimal("1000"), "acompte");

        assertEquals(0, new BigDecimal("2500.00").compareTo(apres.getTotal()), "3000 - 500");
        assertEquals(0, new BigDecimal("1000.00").compareTo(apres.getPaidAmount()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(apres.getDueAmount()));
    }

    // ---------------------------------------------------------------- remise

    @Test
    @DisplayName("Remise contre espèces : solde encaissé en caisse, statut Livrée, garantie démarrée")
    void remiseContreEspecesSoldeLeDossier() {
        RepairTicket t = sav.create(depot("Connecteur HS", null, null));
        sav.updateLabor(t.getId(), new BigDecimal("5000"), BigDecimal.ZERO);
        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "ok");
        sav.changeStatus(t.getId(), RepairStatus.EN_REPARATION, "ok");
        sav.changeStatus(t.getId(), RepairStatus.PRETE, "prêt");

        RepairTicket livre = sav.deliver(t.getId(), "ESPECES", new BigDecimal("5000"), "solde");
        assertEquals(RepairStatus.LIVREE, livre.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(livre.getDueAmount()));
        assertNotNull(livre.getCompletedAt());
        assertNotNull(livre.warrantyEnd(), "la garantie démarre à la remise");
        assertEquals(1, db.cashMovements.size(), "le solde espèces alimente la caisse");
    }

    @Test
    @DisplayName("Remise espèces : un règlement partiel est refusé")
    void remiseEspecesExigeLeSoldeComplet() {
        RepairTicket t = sav.create(depot("Nettoyage", null, null));
        sav.updateLabor(t.getId(), new BigDecimal("4000"), BigDecimal.ZERO);
        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "ok");
        sav.changeStatus(t.getId(), RepairStatus.EN_REPARATION, "ok");
        sav.changeStatus(t.getId(), RepairStatus.PRETE, "ok");

        ValidationException erreur = assertThrows(ValidationException.class,
                () -> sav.deliver(t.getId(), "ESPECES", new BigDecimal("1000"), "trop peu"));
        assertTrue(erreur.getMessage().toLowerCase().contains("solde"));
        assertEquals(RepairStatus.PRETE, sav.byId(t.getId()).getStatus());
        assertTrue(db.cashMovements.isEmpty(), "aucun encaissement partiel");
    }

    @Test
    @DisplayName("Remise à crédit : le solde est porté sur le compte du client")
    void remiseACreditPorteLeSoldeEnDette() {
        RepairTicket t = sav.create(depot("Vitre", null, null));
        sav.updateLabor(t.getId(), new BigDecimal("7000"), BigDecimal.ZERO);
        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "ok");
        sav.changeStatus(t.getId(), RepairStatus.EN_REPARATION, "ok");
        sav.changeStatus(t.getId(), RepairStatus.PRETE, "ok");

        RepairTicket livre = sav.deliver(t.getId(), PaymentMethod.CREDIT.name(), BigDecimal.ZERO,
                "mis en dette");
        assertEquals(RepairStatus.LIVREE, livre.getStatus());
        assertEquals(0, new BigDecimal("7000.00").compareTo(db.balances.get(1)));
        assertEquals(0, new BigDecimal("7000.00").compareTo(livre.getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(livre.getDueAmount()));
    }

    // ----------------------------------------------------------- annulation

    @Test
    @DisplayName("Annulation : les pièces consommées reviennent au stock")
    void annulationRestitueLesPieces() {
        piece(1, 10);
        RepairTicket t = sav.create(depot("Carte mère", null, null));
        sav.changeStatus(t.getId(), RepairStatus.DIAGNOSTIC, "ok");
        sav.addPart(t.getId(), 1, 3, new BigDecimal("4000"), null);
        assertEquals(7, db.quantityOf(1));

        sav.changeStatus(t.getId(), RepairStatus.ANNULEE, "client refuse le devis");
        assertEquals(RepairStatus.ANNULEE, sav.byId(t.getId()).getStatus());
        assertEquals(10, db.quantityOf(1), "les 3 pièces sont restituées");
    }

    // -------------------------------------------------------- notifications

    @Test
    @DisplayName("Notification client simulée et journalisée")
    void notificationClientEstJournalisee() {
        RepairTicket t = sav.create(depot("Micro", null, null));
        NotificationLog n = sav.notifier(t.getId(), "SMS", "Votre appareil est prêt");

        assertEquals("SMS", n.channel());
        assertEquals(NotificationLog.SIMULE, n.status());
        assertEquals(1, sav.notifications(t.getId()).size());
    }

    // ================================================================ doubles

    /** DAO dossier : stocke les en-têtes en mémoire et applique les écritures. */
    private static final class FakeRepairDao extends RepairTicketDao {

        final Map<Integer, RepairTicket> store = new LinkedHashMap<>();
        private int seq;

        @Override
        public long nextNumber() {
            return seq + 1L;
        }

        @Override
        public int insert(RepairTicket t) throws SQLException {
            int id = ++seq;
            t.setId(id);
            if (t.getNumber() == null || t.getNumber().isBlank()) {
                t.setNumber("R-" + String.format("%06d", id));
            }
            store.put(id, t);
            return id;
        }

        @Override
        public Optional<RepairTicket> byId(int id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public void updateStatus(int id, String status, LocalDateTime completedAt) {
            RepairTicket t = store.get(id);
            if (t != null) {
                t.setStatus(status);
                t.setCompletedAt(completedAt);
            }
        }

        @Override
        public void updateDiagnosis(int id, String diagnosis, String internalNotes) {
            RepairTicket t = store.get(id);
            if (t != null) {
                t.setDiagnosis(diagnosis);
                t.setInternalNotes(internalNotes);
            }
        }

        @Override
        public void updateTotals(int id, BigDecimal laborCost, BigDecimal partsTotal,
                                 BigDecimal discount, BigDecimal total, BigDecimal deposit,
                                 BigDecimal paid, BigDecimal due) {
            RepairTicket t = store.get(id);
            if (t != null) {
                t.setLaborCost(laborCost);
                t.setPartsTotal(partsTotal);
                t.setDiscount(discount);
                t.setTotal(total);
                t.setDepositAmount(deposit);
                t.setPaidAmount(paid);
                t.setDueAmount(due);
            }
        }
    }

    /** DAO chronologie. */
    private static final class FakeHistoryDao extends RepairStatusHistoryDao {

        final List<RepairStatusHistory> list = new ArrayList<>();
        private long seq;

        @Override
        public long insert(int repairId, String oldStatus, String newStatus, String comment,
                           Integer userId) {
            long id = ++seq;
            list.add(new RepairStatusHistory(id, repairId, oldStatus, newStatus, comment, userId,
                    null, LocalDateTime.now()));
            return id;
        }

        @Override
        public List<RepairStatusHistory> listByRepair(int repairId) {
            return list.stream().filter(h -> h.repairId() == repairId).toList();
        }
    }

    /** DAO pièces. */
    private static final class FakePartDao extends RepairPartDao {

        final List<RepairPart> list = new ArrayList<>();
        private int seq;

        @Override
        public int insert(RepairPart p) {
            int id = ++seq;
            list.add(new RepairPart(id, p.repairId(), p.productId(), p.productName(),
                    p.productUnitId(), p.quantity(), p.unitCost(), p.unitPrice(), p.total(),
                    p.stockMovementId(), p.addedAt() == null ? LocalDateTime.now() : p.addedAt(),
                    p.userId()));
            return id;
        }

        @Override
        public Optional<RepairPart> findById(long id) {
            return list.stream().filter(p -> p.id() == id).findFirst();
        }

        @Override
        public void delete(long id) {
            list.removeIf(p -> p.id() == id);
        }

        @Override
        public List<RepairPart> listByRepair(int repairId) {
            return list.stream().filter(p -> p.repairId() == repairId).toList();
        }
    }

    /** DAO paiements / acomptes. */
    private static final class FakePaymentDao extends RepairPaymentDao {

        final List<RepairPayment> list = new ArrayList<>();
        private int seq;

        @Override
        public int insert(RepairPayment p) {
            int id = ++seq;
            list.add(new RepairPayment(id, p.repairId(), p.paymentDate(), p.method(), p.amount(),
                    p.cashSessionId(), p.note(), p.userId()));
            return id;
        }

        @Override
        public List<RepairPayment> listByRepair(int repairId) {
            return list.stream().filter(p -> p.repairId() == repairId).toList();
        }
    }

    /** DAO pièces jointes (non exercé ici, requis par le constructeur). */
    private static final class FakeAttachmentDao extends RepairAttachmentDao {

        final List<RepairAttachment> list = new ArrayList<>();
        private int seq;

        @Override
        public int insert(RepairAttachment a) {
            int id = ++seq;
            list.add(new RepairAttachment(id, a.repairId(), a.fileName(), a.filePath(),
                    a.mimeType(), a.sizeBytes(), a.uploadedAt(), a.userId()));
            return id;
        }

        @Override
        public void delete(int id) {
            list.removeIf(a -> a.id() == id);
        }

        @Override
        public List<RepairAttachment> listByRepair(int repairId) {
            return list.stream().filter(a -> a.repairId() == repairId).toList();
        }
    }

    /** DAO journal des notifications. */
    private static final class FakeNotificationDao extends NotificationLogDao {

        final List<NotificationLog> list = new ArrayList<>();
        private long seq;

        @Override
        public long insert(NotificationLog n) {
            long id = ++seq;
            list.add(new NotificationLog(id, n.channel(), n.customerId(), n.repairId(), n.saleId(),
                    n.recipient(), n.message(), n.status(), n.createdAt(), n.userId()));
            return id;
        }

        @Override
        public List<NotificationLog> listByRepair(int repairId) {
            return list.stream()
                    .filter(n -> n.repairId() != null && n.repairId() == repairId)
                    .toList();
        }
    }

}
