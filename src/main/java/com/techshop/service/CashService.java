package com.techshop.service;

import com.techshop.dao.CashMovementDao;
import com.techshop.dao.CashSessionDao;
import com.techshop.dao.SaleDao;
import com.techshop.dao.SalePaymentDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.CashMovement;
import com.techshop.model.CashMovementType;
import com.techshop.model.CashSession;
import com.techshop.model.PaymentMethod;
import com.techshop.util.ValidationException;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service des sessions de caisse (SPEC §4.10). Une session est ouverte avec un
 * fonds initial, alimentée par les encaissements / sorties (cash_movements) et
 * clôturée par un comptage dont l'écart est conservé. Aucune vente n'est
 * possible sans session ouverte ({@link #requireOpen()}).
 */
public final class CashService {

    private static final Logger log = LoggerFactory.getLogger(CashService.class);

    private static final CashService INSTANCE = new CashService(
            new CashSessionDao(), new CashMovementDao(), new SalePaymentDao(), new SaleDao(),
            ActivityLogService.get());

    private final CashSessionDao sessions;
    private final CashMovementDao movements;
    private final SalePaymentDao payments;
    private final SaleDao sales;
    private final ActivityAudit journal;

    /** État exposé à la barre supérieure (indicateur de caisse). */
    private final BooleanProperty open = new SimpleBooleanProperty(false);
    private final StringProperty sessionNumber = new SimpleStringProperty("");

    public CashService(CashSessionDao sessions, CashMovementDao movements, SalePaymentDao payments,
                       SaleDao sales, ActivityAudit journal) {
        this.sessions = sessions;
        this.movements = movements;
        this.payments = payments;
        this.sales = sales;
        this.journal = journal;
    }

    public static CashService get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ état

    public ReadOnlyBooleanProperty openProperty() {
        return open;
    }

    public ReadOnlyStringProperty sessionNumberProperty() {
        return sessionNumber;
    }

    /** Lit l'état de caisse du jour et met à jour l'indicateur. */
    public void refreshState() {
        try {
            Optional<CashSession> s = currentSession();
            open.set(s.isPresent());
            sessionNumber.set(s.map(CashSession::getNumber).orElse(""));
        } catch (RuntimeException e) {
            open.set(false);
            sessionNumber.set("");
        }
    }

    /** Session ouverte de l'utilisateur courant (ou toute session ouverte). */
    public Optional<CashSession> currentSession() {
        try {
            Integer userId = userId();
            if (userId != null) {
                Optional<CashSession> s = sessions.findOpenByUser(userId);
                if (s.isPresent()) {
                    return s;
                }
            }
            return sessions.findAnyOpen();
        } catch (SQLException e) {
            log.error("Lecture de la session de caisse impossible", e);
            return Optional.empty();
        }
    }

    public boolean isOpen() {
        return currentSession().isPresent();
    }

    /** Session en cours ou refus explicite (prérequis d'une vente). */
    public CashSession requireOpen() {
        return currentSession().orElseThrow(() -> new ValidationException(
                "Aucune session de caisse ouverte. Ouvrez la caisse avant d'encaisser une vente."));
    }

    // -------------------------------------------------------------- ouverture

    /** Ouvre une session avec son fonds initial. Droit : créer (caisse). */
    public CashSession openSession(BigDecimal openingFloat, String note) {
        PermissionService.get().check(PermissionDefaults.M_CASH, PermissionService.Action.CREATE);
        if (openingFloat == null || openingFloat.signum() < 0) {
            throw new ValidationException("Le fonds initial ne peut pas être négatif.");
        }
        try {
            Integer userId = userId();
            if (userId == null) {
                throw new ValidationException("Aucun utilisateur connecté.");
            }
            if (sessions.findOpenByUser(userId).isPresent()) {
                throw new ValidationException("Une session de caisse est déjà ouverte.");
            }
            CashSession s = new CashSession();
            s.setUserId(userId);
            s.setOpenedAt(LocalDateTime.now());
            s.setOpeningFloat(openingFloat);
            s.setStatus(CashSession.OUVERTE);
            s.setNote(note);
            sessions.insert(s);
            journal.log(userId, ActivityActions.CAISSE_OUVERTURE, "cash_sessions", s.getId(),
                    "Ouverture " + s.getNumber() + " — fonds " + com.techshop.util.Format.money(openingFloat));
            refreshState();
            return s;
        } catch (SQLException e) {
            log.error("Ouverture de caisse impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // --------------------------------------------------------------- clôture

    /** Clôture avec le montant compté (toutes coupures confondues). Droit : valider. */
    public CashSession closeSession(BigDecimal countedCash, String note) {
        PermissionService.get().check(PermissionDefaults.M_CASH, PermissionService.Action.VALIDATE);
        if (countedCash == null || countedCash.signum() < 0) {
            throw new ValidationException("Le montant compté ne peut pas être négatif.");
        }
        try {
            CashSession s = requireOpen();
            SessionState st = state(s.getId(), s);
            BigDecimal ecart = countedCash.subtract(st.expectedCash());
            sessions.close(s.getId(), st.expectedCash(), countedCash, ecart, st.expectedCard(),
                    st.expectedTransfer(), st.totalSales(), note);
            s.setExpectedCash(st.expectedCash());
            s.setCountedCash(countedCash);
            s.setDifference(ecart);
            s.setExpectedCard(st.expectedCard());
            s.setExpectedTransfer(st.expectedTransfer());
            s.setTotalSales(st.totalSales());
            s.setStatus(CashSession.FERMEE);
            s.setClosedAt(LocalDateTime.now());
            s.setNote(note);
            journal.log(userId(), ActivityActions.CAISSE_CLOTURE, "cash_sessions", s.getId(),
                    "Clôture " + s.getNumber() + " — écart " + com.techshop.util.Format.money(ecart));
            refreshState();
            return s;
        } catch (SQLException e) {
            log.error("Clôture de caisse impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ------------------------------------------------------------- mouvements

    /** Entrée / sortie d'espèces avec motif. Droit : créer (caisse). */
    public CashMovement addMovement(CashMovementType type, BigDecimal amount, String reason,
                                    Integer categoryId) {
        PermissionService.get().check(PermissionDefaults.M_CASH, PermissionService.Action.CREATE);
        if (type == null) {
            throw new ValidationException("Type de mouvement manquant.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Le montant doit être supérieur à zéro.");
        }
        if (type == CashMovementType.SORTIE && (reason == null || reason.isBlank())) {
            throw new ValidationException("Le motif est obligatoire pour une sortie de caisse.");
        }
        try {
            CashSession s = requireOpen();
            CashMovement m = new CashMovement();
            m.setCashSessionId(s.getId());
            m.setType(type.name());
            m.setAmount(amount);
            m.setReason(reason);
            m.setCategoryId(categoryId);
            m.setUserId(userId() == null ? 0 : userId());
            m.setMovementDate(LocalDateTime.now());
            movements.insert(m);
            journal.log(userId(), ActivityActions.CAISSE_MOUVEMENT, "cash_movements", (int) m.getId(),
                    type.getLabel() + " " + com.techshop.util.Format.money(amount)
                            + (reason != null && !reason.isBlank() ? " — " + reason : ""));
            return m;
        } catch (SQLException e) {
            log.error("Mouvement de caisse impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /**
     * Enregistre l'encaissement en espèces d'une vente (appelé par le
     * SaleService dans la même transaction). Aucun contrôle de droit ici.
     */
    public void recordSaleCash(int sessionId, int saleId, BigDecimal cashAmount, Integer userId) {
        if (cashAmount == null || cashAmount.signum() <= 0) {
            return;
        }
        try {
            CashMovement m = new CashMovement();
            m.setCashSessionId(sessionId);
            m.setType(CashMovementType.ENTREE.name());
            m.setAmount(cashAmount);
            m.setReason("Encaissement vente");
            m.setReferenceType("SALE");
            m.setReferenceId(saleId);
            m.setUserId(userId == null ? 0 : userId);
            m.setMovementDate(LocalDateTime.now());
            movements.insert(m);
        } catch (SQLException e) {
            throw new ValidationException("Encaissement de la vente en caisse impossible : "
                    + e.getMessage());
        }
    }

    /** Enregistre un remboursement espèces (retour). */
    public void recordRefundCash(int sessionId, int returnId, BigDecimal cashAmount, Integer userId) {
        if (cashAmount == null || cashAmount.signum() <= 0) {
            return;
        }
        try {
            CashMovement m = new CashMovement();
            m.setCashSessionId(sessionId);
            m.setType(CashMovementType.SORTIE.name());
            m.setAmount(cashAmount);
            m.setReason("Remboursement retour");
            m.setReferenceType("SALE_RETURN");
            m.setReferenceId(returnId);
            m.setUserId(userId == null ? 0 : userId);
            m.setMovementDate(LocalDateTime.now());
            movements.insert(m);
        } catch (SQLException e) {
            throw new ValidationException("Remboursement en caisse impossible : " + e.getMessage());
        }
    }

    /**
     * Enregistre un encaissement espèces lié à une réparation (acompte ou solde),
     * appelé par le RepairService dans la même transaction. Aucun contrôle de
     * droit ici.
     */
    public void recordRepairCash(int sessionId, int repairId, BigDecimal cashAmount, Integer userId) {
        if (cashAmount == null || cashAmount.signum() <= 0) {
            return;
        }
        try {
            CashMovement m = new CashMovement();
            m.setCashSessionId(sessionId);
            m.setType(CashMovementType.ENTREE.name());
            m.setAmount(cashAmount);
            m.setReason("Encaissement réparation");
            m.setReferenceType("REPAIR");
            m.setReferenceId(repairId);
            m.setUserId(userId == null ? 0 : userId);
            m.setMovementDate(LocalDateTime.now());
            movements.insert(m);
        } catch (SQLException e) {
            throw new ValidationException("Encaissement de la réparation en caisse impossible : "
                    + e.getMessage());
        }
    }

    /**
     * Enregistre un encaissement espèces d'une dette client, appelé par
     * {@link CustomerPaymentService} dans la même transaction. Aucun contrôle
     * de droit ici.
     */
    public void recordCustomerCash(int sessionId, int paymentId, BigDecimal cashAmount,
                                   Integer userId) {
        if (cashAmount == null || cashAmount.signum() <= 0) {
            return;
        }
        try {
            CashMovement m = new CashMovement();
            m.setCashSessionId(sessionId);
            m.setType(CashMovementType.ENTREE.name());
            m.setAmount(cashAmount);
            m.setReason("Encaissement dette client");
            m.setReferenceType("CUSTOMER_PAYMENT");
            m.setReferenceId(paymentId);
            m.setUserId(userId == null ? 0 : userId);
            m.setMovementDate(LocalDateTime.now());
            movements.insert(m);
        } catch (SQLException e) {
            throw new ValidationException("Encaissement de la dette en caisse impossible : "
                    + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- lectures

    /** Synthèse calculée d'une session (attendus, ventes). */
    public record SessionState(BigDecimal openingFloat, BigDecimal cashIn, BigDecimal cashOut,
                               BigDecimal expectedCash, BigDecimal expectedCard,
                               BigDecimal expectedTransfer, BigDecimal totalSales, int salesCount,
                               long movementCount) {
    }

    public SessionState state(int sessionId, CashSession session) {
        try {
            BigDecimal cashIn = movements.sumByType(sessionId, CashMovementType.ENTREE.name());
            BigDecimal cashOut = movements.sumByType(sessionId, CashMovementType.SORTIE.name());
            BigDecimal opening = session == null ? BigDecimal.ZERO : session.getOpeningFloat();
            BigDecimal expected = opening.add(cashIn).subtract(cashOut);
            return new SessionState(opening, cashIn, cashOut, expected,
                    payments.sumBySessionAndMethod(sessionId, PaymentMethod.CARTE.name()),
                    payments.sumBySessionAndMethod(sessionId, PaymentMethod.VIREMENT.name()),
                    sales.sumTotalBySession(sessionId), (int) sales.countBySession(sessionId),
                    movements.countBySession(sessionId));
        } catch (SQLException e) {
            log.error("Calcul de la caisse impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public List<CashMovement> movements(int sessionId) {
        try {
            return movements.listBySession(sessionId);
        } catch (SQLException e) {
            log.error("Lecture des mouvements de caisse impossible", e);
            return List.of();
        }
    }

    public List<CashSession> recentSessions(int limit) {
        try {
            return sessions.recent(limit);
        } catch (SQLException e) {
            log.error("Lecture de l'historique de caisse impossible", e);
            return List.of();
        }
    }

    public java.util.Optional<CashSession> byId(int id) {
        try {
            return sessions.byId(id);
        } catch (SQLException e) {
            log.error("Lecture de la session de caisse impossible", e);
            return java.util.Optional.empty();
        }
    }

    private static Integer userId() {
        var u = SessionContext.get().getUser();
        return u == null ? null : u.getId();
    }
}
