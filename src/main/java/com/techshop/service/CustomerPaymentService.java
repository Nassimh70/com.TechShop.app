package com.techshop.service;

import com.techshop.config.Tx;
import com.techshop.dao.CustomerDao;
import com.techshop.dao.CustomerPaymentDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.CashSession;
import com.techshop.model.Customer;
import com.techshop.model.CustomerPayment;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Règlements de dettes clients (SPEC §4.7) : encaissement partiel ou total,
 * daté, et correction de la date d'un règlement. Chaque encaissement diminue
 * le solde du client, alimente la caisse ouverte (espèces) et le journal, le
 * tout dans une seule transaction.
 */
public final class CustomerPaymentService {

    private static final Logger log = LoggerFactory.getLogger(CustomerPaymentService.class);

    private static final CustomerPaymentService INSTANCE = new CustomerPaymentService(
            new CustomerDao(), new CustomerPaymentDao(), CashService.get(),
            ActivityLogService.get());

    private final CustomerDao customers;
    private final CustomerPaymentDao payments;
    private final CashService cash;
    private final ActivityAudit journal;

    public CustomerPaymentService(CustomerDao customers, CustomerPaymentDao payments,
                                  CashService cash, ActivityAudit journal) {
        this.customers = customers;
        this.payments = payments;
        this.cash = cash;
        this.journal = journal;
    }

    public static CustomerPaymentService get() {
        return INSTANCE;
    }

    /**
     * Encaisse un règlement de dette (partiel ou total).
     *
     * @param customerId client concerné
     * @param montant    montant encaissé (positif, au plus égal à la dette)
     * @param date       date et heure du règlement (maintenant si null)
     * @param mode       ESPECES, CARTE ou VIREMENT
     * @param note       note libre (facultatif)
     */
    public CustomerPayment encaisser(int customerId, BigDecimal montant, LocalDateTime date,
                                     String mode, String note) {
        PermissionService.get().check(PermissionDefaults.M_CUSTOMERS,
                PermissionService.Action.EDIT);
        BigDecimal valeur = montant == null ? BigDecimal.ZERO
                : montant.setScale(2, RoundingMode.HALF_UP);
        if (valeur.signum() <= 0) {
            throw new ValidationException("Le montant doit être supérieur à zéro.");
        }
        LocalDateTime quand = date == null ? LocalDateTime.now() : date;
        CustomerPayment.Method methode = methode(mode);
        int uid = userIdOrFallback();

        try {
            return Tx.run(() -> {
                Customer client = customers.byId(customerId).orElseThrow(
                        () -> new ValidationException("Client introuvable."));
                BigDecimal dette = client.getBalance();
                if (dette.signum() <= 0) {
                    throw new ValidationException("Ce client n'a aucune dette à encaisser.");
                }
                if (valeur.compareTo(dette) > 0) {
                    throw new ValidationException("Le montant dépasse la dette restante ("
                            + Format.money(dette) + ").");
                }

                // Un règlement antidaté corrige l'historique : il ne doit pas
                // alimenter la caisse ouverte aujourd'hui.
                boolean aujourdHui = quand.toLocalDate().equals(LocalDate.now());
                CashSession session = aujourdHui ? cash.currentSession().orElse(null) : null;
                CustomerPayment p = new CustomerPayment();
                p.setCustomerId(customerId);
                p.setAmount(valeur);
                p.setPaymentMethod(methode);
                p.setPaymentDate(quand);
                p.setCashSessionId(session == null ? null : session.getId());
                p.setNote(note == null || note.isBlank() ? null : note.trim());
                p.setUserId(uid);
                if (!customers.decreaseBalance(customerId, valeur)) {
                    throw new ValidationException(
                            "La dette a changé. Rechargez le client puis réessayez.");
                }
                client.setBalance(dette.subtract(valeur));

                int id = payments.insert(p);
                p.setId(id);

                if (session != null && methode == CustomerPayment.Method.ESPECES) {
                    cash.recordCustomerCash(session.getId(), id, valeur, uid);
                }

                journal.log(uid, ActivityActions.ENCAISSEMENT_DETTE, "customer_payments", id,
                        "Client " + client.getCode() + " - " + Format.money(valeur)
                                + " (" + methode.name() + ")");
                return p;
            });
        } catch (SQLException e) {
            log.error("Encaissement d'une dette client impossible", e);
            throw new ValidationException(
                    "Encaissement impossible : base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Corrige la date (et la note) d'un règlement existant. */
    public void modifierDate(int paymentId, LocalDateTime date, String note) {
        PermissionService.get().check(PermissionDefaults.M_CUSTOMERS,
                PermissionService.Action.EDIT);
        if (date == null) {
            throw new ValidationException("La date du règlement est obligatoire.");
        }
        try {
            CustomerPayment p = payments.byId(paymentId).orElseThrow(
                    () -> new ValidationException("Règlement introuvable."));
            payments.updateDate(paymentId, date,
                    note == null || note.isBlank() ? null : note.trim());
            journal.log(userIdOrFallback(), ActivityActions.ENCAISSEMENT_DETTE,
                    "customer_payments", paymentId,
                    "Correction de date - " + Format.money(p.getAmount()) + " -> "
                            + Format.dateTime(date));
        } catch (SQLException e) {
            log.error("Correction d'un règlement client impossible", e);
            throw new ValidationException(
                    "Enregistrement impossible : base de données indisponible. Réessayez plus tard.");
        }
    }

    private static CustomerPayment.Method methode(String mode) {
        if (mode != null) {
            String cible = sansAccents(mode);
            for (CustomerPayment.Method m : CustomerPayment.Method.values()) {
                if (sansAccents(m.name()).equals(cible)) {
                    return m;
                }
            }
        }
        return CustomerPayment.Method.ESPECES;
    }

    private static String sansAccents(String valeur) {
        return Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT);
    }

    private static int userIdOrFallback() {
        var u = SessionContext.get().getUser();
        return u == null ? 1 : u.getId();
    }
}
