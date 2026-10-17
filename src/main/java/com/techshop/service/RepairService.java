package com.techshop.service;

import com.techshop.config.Tx;
import com.techshop.dao.CustomerDao;
import com.techshop.dao.NotificationLogDao;
import com.techshop.dao.ProductDao;
import com.techshop.dao.RepairAttachmentDao;
import com.techshop.dao.RepairPartDao;
import com.techshop.dao.RepairPaymentDao;
import com.techshop.dao.RepairStatusHistoryDao;
import com.techshop.dao.RepairTicketDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.CashSession;
import com.techshop.model.NotificationLog;
import com.techshop.model.PaymentMethod;
import com.techshop.model.Product;
import com.techshop.model.RepairAttachment;
import com.techshop.model.RepairPart;
import com.techshop.model.RepairPayment;
import com.techshop.model.RepairStatus;
import com.techshop.model.RepairStatusHistory;
import com.techshop.model.RepairTicket;
import com.techshop.model.StockMovement;
import com.techshop.model.StockMovementType;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service après-vente (SPEC §4.9, §6). Gère le cycle complet d'un dossier de
 * réparation : dépôt, transitions de statut contrôlées, consommation de pièces
 * (sortie de stock), main d'œuvre, acomptes, notification client et remise de
 * l'appareil (solde payé ou mis en dette). Toute opération multi-tables est
 * exécutée dans une transaction unique.
 */
public final class RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairService.class);

    private static final RepairService INSTANCE = new RepairService(
            new RepairTicketDao(), new RepairStatusHistoryDao(), new RepairPartDao(),
            new RepairPaymentDao(), new RepairAttachmentDao(), new NotificationLogDao(),
            new ProductDao(), new CustomerDao(), StockService.get(), CashService.get(),
            ActivityLogService.get());

    private final RepairTicketDao tickets;
    private final RepairStatusHistoryDao history;
    private final RepairPartDao parts;
    private final RepairPaymentDao payments;
    private final RepairAttachmentDao attachments;
    private final NotificationLogDao notifications;
    private final ProductDao products;
    private final CustomerDao customers;
    private final StockService stock;
    private final CashService cash;
    private final ActivityAudit journal;

    public RepairService(RepairTicketDao tickets, RepairStatusHistoryDao history, RepairPartDao parts,
                         RepairPaymentDao payments, RepairAttachmentDao attachments,
                         NotificationLogDao notifications, ProductDao products, CustomerDao customers,
                         StockService stock, CashService cash, ActivityAudit journal) {
        this.tickets = tickets;
        this.history = history;
        this.parts = parts;
        this.payments = payments;
        this.attachments = attachments;
        this.notifications = notifications;
        this.products = products;
        this.customers = customers;
        this.stock = stock;
        this.cash = cash;
        this.journal = journal;
    }

    public static RepairService get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ types

    /** Demande de dépôt (dialogue en 4 étapes, SPEC §4.9). */
    public record DepositRequest(Integer customerId, String deviceType, String brand, String model,
                                 String serialOrImei, String devicePassword, String accessories,
                                 String physicalCondition, String reportedIssue, String diagnosis,
                                 BigDecimal quotedAmount, String depositMethod, BigDecimal deposit,
                                 LocalDate promisedDate, Integer technicianId, int warrantyDays,
                                 String internalNotes) {
    }

    // ------------------------------------------------------------------- dépôt

    /** Crée un dossier de réparation au statut « Reçu ». */
    public RepairTicket create(DepositRequest req) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.CREATE);
        if (req == null || req.customerId() == null) {
            throw new ValidationException("Un client est obligatoire pour ouvrir un dossier.");
        }
        if (req.reportedIssue() == null || req.reportedIssue().isBlank()) {
            throw new ValidationException("La panne déclarée est obligatoire.");
        }
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = new RepairTicket();
                t.setCustomerId(req.customerId());
                t.setDeviceType(nz(req.deviceType()));
                t.setBrand(nz(req.brand()));
                t.setModel(nz(req.model()));
                t.setSerialOrImei(nz(req.serialOrImei()));
                t.setDevicePassword(nz(req.devicePassword()));
                t.setAccessories(nz(req.accessories()));
                t.setPhysicalCondition(nz(req.physicalCondition()));
                t.setReportedIssue(req.reportedIssue());
                t.setDiagnosis(nz(req.diagnosis()));
                t.setQuotedAmount(money(req.quotedAmount()));
                t.setStatus(RepairStatus.RECU);
                t.setTechnicianId(req.technicianId());
                t.setReceivedAt(LocalDateTime.now());
                t.setPromisedDate(req.promisedDate());
                t.setWarrantyDays(Math.max(0, req.warrantyDays()));
                t.setInternalNotes(nz(req.internalNotes()));
                tickets.insert(t);
                history.insert(t.getId(), null, RepairStatus.RECU, "Dépôt de l'appareil", uid);

                // Acompte éventuel
                BigDecimal acompte = money(req.deposit());
                if (acompte.signum() > 0) {
                    String methode = normaliserMethode(req.depositMethod());
                    enregistrerPaiement(t, methode, acompte, "Acompte au dépôt", uid);
                }
                persistTotals(t);
                recharger(t);
                journal.log(uid, ActivityActions.REPARATION, "repair_tickets", t.getId(),
                        "Dépôt " + t.getNumber() + " — " + t.deviceLabel());
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Ouverture du dossier de réparation impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // -------------------------------------------------------------- statut

    /** Change le statut d'un dossier en respectant les transitions autorisées. */
    public RepairTicket changeStatus(int repairId, String nouveau, String comment) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        if (RepairStatus.LIVREE.equals(nouveau)) {
            throw new ValidationException("La remise de l'appareil se fait depuis « Remettre l'appareil ».");
        }
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                String ancien = t.getStatus();
                if (!RepairStatus.canTransition(ancien, nouveau)) {
                    throw new ValidationException("Transition impossible : "
                            + RepairStatus.label(ancien) + " → " + RepairStatus.label(nouveau) + ".");
                }
                t.setStatus(nouveau);
                if (RepairStatus.ANNULEE.equals(nouveau)) {
                    restituerPieces(t);
                    t.setCompletedAt(LocalDateTime.now());
                    tickets.updateStatus(t.getId(), nouveau, t.getCompletedAt());
                } else {
                    tickets.updateStatus(t.getId(), nouveau, null);
                }
                history.insert(t.getId(), ancien, nouveau, nz(comment), uid);
                journal.log(uid, ActivityActions.REPARATION_STATUT, "repair_tickets", t.getId(),
                        t.getNumber() + " : " + RepairStatus.label(ancien) + " → "
                                + RepairStatus.label(nouveau));
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Changement de statut impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // -------------------------------------------------------------- pièces

    /** Ajoute une pièce au dossier en la sortant du stock (SPEC §6). */
    public RepairTicket addPart(int repairId, int productId, int quantity, BigDecimal unitPrice,
                                Integer unitId) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        if (quantity <= 0) {
            throw new ValidationException("La quantité doit être supérieure à zéro.");
        }
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                if (RepairStatus.isTerminal(t.getStatus())) {
                    throw new ValidationException("Ce dossier est clôturé : impossible d'ajouter une pièce.");
                }
                Product p = products.findById(productId).orElseThrow(
                        () -> new ValidationException("Produit introuvable."));
                if (p.getQuantity() < quantity) {
                    throw new ValidationException("Stock insuffisant pour « " + p.getName()
                            + " » (" + p.getQuantity() + " disponible" + (p.getQuantity() > 1 ? "s" : "")
                            + ").");
                }
                BigDecimal cout = p.getAvgCost() == null ? nz(p.getPurchasePrice()) : money(p.getAvgCost());
                BigDecimal prix = unitPrice == null || unitPrice.signum() <= 0
                        ? money(p.getSalePrice()) : money(unitPrice);
                BigDecimal total = prix.multiply(BigDecimal.valueOf(quantity));

                StockMovement mvt = stock.apply(productId, StockMovementType.REPARATION, quantity,
                        unitId, cout, "Pièce " + t.getNumber(), "REPAIR", t.getId());
                RepairPart part = new RepairPart(0, t.getId(), productId, null, unitId, quantity, cout,
                        prix, total, mvt.id(), LocalDateTime.now(), userIdOrFallback());
                int id = parts.insert(part);
                t.getParts().add(new RepairPart(id, part.repairId(), part.productId(), part.productName(),
                        part.productUnitId(), part.quantity(), part.unitCost(), part.unitPrice(),
                        part.total(), part.stockMovementId(), part.addedAt(), part.userId()));
                persistTotals(t);
                journal.log(userIdOrFallback(), ActivityActions.REPARATION_PIECE, "repair_parts", id,
                        t.getNumber() + " — pièce ajoutée (" + quantity + " × " + Format.money(prix) + ")");
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Ajout de pièce impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Met à jour la main d'œuvre et la remise globale du dossier. */
    public RepairTicket updateLabor(int repairId, BigDecimal laborCost, BigDecimal discount) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                t.setLaborCost(money(laborCost));
                t.setDiscount(money(discount));
                persistTotals(t);
                journal.log(uid, ActivityActions.REPARATION, "repair_tickets", t.getId(),
                        t.getNumber() + " — main d'œuvre " + Format.money(t.getLaborCost()));
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Mise à jour de la main d'œuvre impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Met à jour le diagnostic et les notes internes du dossier (SPEC §4.9). */
    public RepairTicket updateDiagnosis(int repairId, String diagnosis, String notes) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                t.setDiagnosis(nz(diagnosis));
                t.setInternalNotes(nz(notes));
                tickets.updateDiagnosis(t.getId(), t.getDiagnosis(), t.getInternalNotes());
                journal.log(uid, ActivityActions.REPARATION, "repair_tickets", t.getId(),
                        t.getNumber() + " — diagnostic mis à jour");
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Mise à jour du diagnostic impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Retire une pièce du dossier et la restitue au stock (SPEC §4.9). */
    public RepairTicket removePart(int partId, int repairId) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                if (RepairStatus.isTerminal(t.getStatus())) {
                    throw new ValidationException("Ce dossier est clôturé : impossible de retirer une pièce.");
                }
                RepairPart p = parts.findById(partId).orElseThrow(
                        () -> new ValidationException("Pièce introuvable."));
                if (p.repairId() != repairId) {
                    throw new ValidationException("Cette pièce n'appartient pas à ce dossier.");
                }
                stock.apply(p.productId(), StockMovementType.RETOUR, p.quantity(), p.productUnitId(),
                        p.unitCost(), "Retrait pièce " + t.getNumber(), "REPAIR", t.getId());
                parts.delete(partId);
                t.getParts().removeIf(x -> x.id() == partId);
                persistTotals(t);
                journal.log(uid, ActivityActions.REPARATION_PIECE, "repair_parts", partId,
                        t.getNumber() + " — pièce retirée (" + p.quantity() + ")");
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Retrait de pièce impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }


    // ----------------------------------------------------------- paiements

    /** Enregistre un acompte / règlement intermédiaire. */
    public RepairTicket addDeposit(int repairId, String method, BigDecimal amount, String note) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        BigDecimal montant = money(amount);
        if (montant.signum() <= 0) {
            throw new ValidationException("Le montant doit être supérieur à zéro.");
        }
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                if (RepairStatus.LIVREE.equals(t.getStatus()) || RepairStatus.ANNULEE.equals(t.getStatus())) {
                    throw new ValidationException("Ce dossier est clôturé.");
                }
                enregistrerPaiement(t, normaliserMethode(method), montant, nz(note), uid);
                persistTotals(t);
                journal.log(uid, ActivityActions.REPARATION_PAIEMENT, "repair_tickets", t.getId(),
                        t.getNumber() + " — acompte " + Format.money(montant));
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Enregistrement de l'acompte impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /**
     * Remise de l'appareil (SPEC §4.9) : solde réglé (espèces en caisse) ou mis
     * en dette sur le compte du client. Le dossier passe au statut « Livrée » et
     * la garantie démarre.
     */
    public RepairTicket deliver(int repairId, String method, BigDecimal amount, String note) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.VALIDATE);
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                if (RepairStatus.LIVREE.equals(t.getStatus())) {
                    throw new ValidationException("Cet appareil a déjà été remis.");
                }
                if (RepairStatus.ANNULEE.equals(t.getStatus())) {
                    throw new ValidationException("Ce dossier est annulé.");
                }
                recharger(t);
                BigDecimal due = nz(t.getDueAmount());
                String methode = normaliserMethode(method);

                if (PaymentMethod.CREDIT.name().equals(methode)) {
                    if (due.signum() > 0) {
                        customers.updateBalance(t.getCustomerId(), due);
                        enregistrerPaiement(t, methode, due, "Solde mis en dette", uid);
                    }
                } else if (due.signum() > 0) {
                    BigDecimal verse = money(amount);
                    if (verse.compareTo(due) < 0) {
                        throw new ValidationException("Le solde de " + Format.money(due)
                                + " doit être réglé en totalité (ou mis en dette).");
                    }
                    enregistrerPaiement(t, methode, due, nz(note), uid);
                }

                t.setStatus(RepairStatus.LIVREE);
                t.setCompletedAt(LocalDateTime.now());
                persistTotals(t);
                tickets.updateStatus(t.getId(), RepairStatus.LIVREE, t.getCompletedAt());
                history.insert(t.getId(), RepairStatus.PRETE, RepairStatus.LIVREE,
                        "Appareil remis — " + methode, uid);
                journal.log(uid, ActivityActions.REPARATION_STATUT, "repair_tickets", t.getId(),
                        t.getNumber() + " — livré (" + Format.money(t.getTotal()) + ")");
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Remise de l'appareil impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // -------------------------------------------------------- notifications

    /** Notification client simulée, journalisée dans notifications_log. */
    public NotificationLog notifier(int repairId, String channel, String message) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        int uid = userIdOrFallback();
        try {
            return Tx.run(() -> {
                RepairTicket t = tickets.byId(repairId).orElseThrow(
                        () -> new ValidationException("Dossier introuvable."));
                String canal = channel == null || channel.isBlank() ? "SMS" : channel.trim().toUpperCase();
                String texte = (message == null || message.isBlank())
                        ? "Votre appareil (" + t.getNumber() + ") est "
                                + RepairStatus.label(t.getStatus()).toLowerCase() + "."
                        : message.trim();
                NotificationLog n = new NotificationLog(0, canal, t.getCustomerId(), t.getId(), null,
                        t.getCustomerPhone(), texte, NotificationLog.SIMULE, LocalDateTime.now(), uid);
                long id = notifications.insert(n);
                journal.log(uid, ActivityActions.NOTIFICATION_CLIENT, "notifications_log", (int) id,
                        canal + " → " + (t.getCustomerPhone() == null ? "client" : t.getCustomerPhone())
                                + " : " + texte);
                return new NotificationLog(id, canal, t.getCustomerId(), t.getId(), null,
                        t.getCustomerPhone(), texte, NotificationLog.SIMULE, n.createdAt(), uid);
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Notification impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public List<NotificationLog> notifications(int repairId) {
        try {
            return notifications.listByRepair(repairId);
        } catch (SQLException e) {
            log.error("Lecture des notifications impossible", e);
            return List.of();
        }
    }

    // ------------------------------------------------------------ pièces jointes

    /** Copie un fichier dans le dossier de l'application et l'attache au dossier. */
    public RepairTicket addAttachment(int repairId, Path source) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        if (source == null || !Files.isRegularFile(source)) {
            throw new ValidationException("Fichier introuvable.");
        }
        int uid = userIdOrFallback();
        try {
            RepairTicket t = tickets.byId(repairId).orElseThrow(
                    () -> new ValidationException("Dossier introuvable."));
            Path dir = Path.of(System.getProperty("user.dir"), "data", "repairs", t.getNumber());
            Files.createDirectories(dir);
            String nom = System.currentTimeMillis() + "-" + source.getFileName();
            Path cible = dir.resolve(nom);
            Files.copy(source, cible, StandardCopyOption.REPLACE_EXISTING);
            long taille = Files.size(cible);
            String type = Files.probeContentType(source);
            return Tx.run(() -> {
                attachments.insert(new RepairAttachment(0, t.getId(), source.getFileName().toString(),
                        cible.toString(), type, taille, LocalDateTime.now(), uid));
                recharger(t);
                return t;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (IOException | SQLException e) {
            log.error("Ajout de pièce jointe impossible", e);
            throw new ValidationException("Ajout de la pièce jointe impossible : " + e.getMessage());
        }
    }

    public void removeAttachment(int attachmentId, int repairId) {
        PermissionService.get().check(PermissionDefaults.M_REPAIRS, PermissionService.Action.EDIT);
        try {
            attachments.delete(attachmentId);
        } catch (SQLException e) {
            log.error("Suppression de la pièce jointe impossible", e);
            throw new ValidationException("Suppression impossible.");
        }
    }

    // --------------------------------------------------------------- lecture

    /** Charge un dossier complet (en-tête + chronologie + pièces + paiements + photos). */
    public RepairTicket byId(int id) {
        try {
            RepairTicket t = tickets.byId(id).orElseThrow(
                    () -> new ValidationException("Dossier introuvable."));
            recharger(t);
            return t;
        } catch (SQLException e) {
            log.error("Lecture du dossier impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public List<RepairTicket> board() {
        try {
            return tickets.board(500);
        } catch (SQLException e) {
            log.error("Lecture du tableau des réparations impossible", e);
            return List.of();
        }
    }

    public List<RepairTicket> search(RepairTicketDao.Filter filter) {
        try {
            return tickets.search(filter, 500, 0);
        } catch (SQLException e) {
            log.error("Recherche des réparations impossible", e);
            return List.of();
        }
    }

    public Map<String, Long> statusCounts() {
        try {
            return tickets.countsByStatus();
        } catch (SQLException e) {
            log.error("Comptage des statuts impossible", e);
            return Map.of();
        }
    }

    public List<RepairTicket> byCustomer(int customerId) {
        try {
            return tickets.byCustomer(customerId, 200);
        } catch (SQLException e) {
            log.error("Lecture des réparations du client impossible", e);
            return List.of();
        }
    }

    public List<RepairStatusHistory> history(int repairId) {
        try {
            return history.listByRepair(repairId);
        } catch (SQLException e) {
            log.error("Lecture de la chronologie impossible", e);
            return List.of();
        }
    }

    // -------------------------------------------------------- tableau de bord

    public long countActive() {
        try {
            return tickets.countActive();
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<RepairTicket> readyForPickup() {
        try {
            return tickets.readyForPickup();
        } catch (SQLException e) {
            return List.of();
        }
    }

    public List<RepairTicket> late() {
        try {
            return tickets.late(LocalDate.now());
        } catch (SQLException e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------ utilitaires

    /** Enregistre un règlement et son éventuel mouvement de caisse. */
    private void enregistrerPaiement(RepairTicket t, String methode, BigDecimal montant, String note,
                                     int uid) throws SQLException {
        Integer sessionId = null;
        if (PaymentMethod.ESPECES.name().equals(methode)) {
            CashSession session = cash.requireOpen();
            sessionId = session.getId();
        }
        RepairPayment p = new RepairPayment(0, t.getId(), LocalDateTime.now(), methode, montant,
                sessionId, note == null || note.isBlank() ? null : note, uid);
        int id = payments.insert(p);
        t.getPayments().add(new RepairPayment(id, p.repairId(), p.paymentDate(), p.method(), p.amount(),
                p.cashSessionId(), p.note(), p.userId()));
        if (sessionId != null) {
            cash.recordRepairCash(sessionId, t.getId(), montant, uid);
        }
    }

    /** Recalcule et persiste pièces, total, acompte, payé et reste dû. */
    private void persistTotals(RepairTicket t) throws SQLException {
        BigDecimal partsTotal = BigDecimal.ZERO;
        for (RepairPart p : t.getParts()) {
            partsTotal = partsTotal.add(money(p.total()));
        }
        BigDecimal labor = money(t.getLaborCost());
        BigDecimal discount = money(t.getDiscount());
        BigDecimal total = labor.add(partsTotal).subtract(discount);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }
        BigDecimal paid = BigDecimal.ZERO;
        for (RepairPayment p : t.getPayments()) {
            paid = paid.add(money(p.amount()));
        }
        BigDecimal due = total.subtract(paid);
        if (due.signum() < 0) {
            due = BigDecimal.ZERO;
        }
        BigDecimal deposit = RepairStatus.LIVREE.equals(t.getStatus()) ? money(t.getDepositAmount()) : total.min(paid);
        tickets.updateTotals(t.getId(), labor, partsTotal, discount, total, deposit, paid, due);
        t.setLaborCost(labor);
        t.setPartsTotal(partsTotal);
        t.setDiscount(discount);
        t.setTotal(total);
        t.setDepositAmount(deposit);
        t.setPaidAmount(paid);
        t.setDueAmount(total.subtract(paid).max(BigDecimal.ZERO));
    }

    /** Recharge les collections depuis la base. */
    private void recharger(RepairTicket t) throws SQLException {
        t.getHistory().clear();
        t.getParts().clear();
        t.getPayments().clear();
        t.getAttachments().clear();
        t.getHistory().addAll(history.listByRepair(t.getId()));
        t.getParts().addAll(parts.listByRepair(t.getId()));
        t.getPayments().addAll(payments.listByRepair(t.getId()));
        t.getAttachments().addAll(attachments.listByRepair(t.getId()));
    }

    /** Restitue au stock les pièces consommées (annulation du dossier). */
    private void restituerPieces(RepairTicket t) throws SQLException {
        for (RepairPart p : parts.listByRepair(t.getId())) {
            stock.apply(p.productId(), StockMovementType.RETOUR, p.quantity(), p.productUnitId(),
                    p.unitCost(), "Annulation " + t.getNumber(), "REPAIR", t.getId());
        }
    }

    private static String normaliserMethode(String methode) {
        if (methode == null || methode.isBlank()) {
            return PaymentMethod.ESPECES.name();
        }
        for (PaymentMethod m : PaymentMethod.values()) {
            if (m.name().equalsIgnoreCase(methode)) {
                return m.name();
            }
        }
        return PaymentMethod.ESPECES.name();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int userIdOrFallback() {
        var u = SessionContext.get().getUser();
        return u == null ? 1 : u.getId();
    }
}
