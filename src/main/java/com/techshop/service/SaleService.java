package com.techshop.service;

import com.techshop.config.Tx;
import com.techshop.dao.CustomerDao;
import com.techshop.dao.ProductDao;
import com.techshop.dao.ProductUnitDao;
import com.techshop.dao.SaleDao;
import com.techshop.dao.SaleItemDao;
import com.techshop.dao.SalePaymentDao;
import com.techshop.dao.SaleReturnDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.CashSession;
import com.techshop.model.PaymentMethod;
import com.techshop.model.Product;
import com.techshop.model.ProductUnit;
import com.techshop.model.Sale;
import com.techshop.model.SaleItem;
import com.techshop.model.SalePayment;
import com.techshop.model.SaleReturn;
import com.techshop.model.StockMovementType;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service de vente (SPEC §4.3, §4.4, §6.2). Toute la vente est enregistrée dans
 * UNE SEULE transaction : en-tête (sales), lignes (sale_items), règlements
 * (sale_payments), mouvements de stock (stock_movements), statut des IMEI,
 * dette du client et mouvement de caisse. En cas d'erreur, tout est annulé.
 */
public final class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private static final SaleService INSTANCE = new SaleService(
            new SaleDao(), new SaleItemDao(), new SalePaymentDao(), new SaleReturnDao(),
            new ProductDao(), new ProductUnitDao(), new CustomerDao(),
            CashService.get(), StockService.get(), ActivityLogService.get());

    private final SaleDao sales;
    private final SaleItemDao items;
    private final SalePaymentDao payments;
    private final SaleReturnDao returns;
    private final ProductDao products;
    private final ProductUnitDao units;
    private final CustomerDao customers;
    private final CashService cash;
    private final StockService stock;
    private final ActivityAudit journal;

    public SaleService(SaleDao sales, SaleItemDao items, SalePaymentDao payments, SaleReturnDao returns,
                       ProductDao products, ProductUnitDao units, CustomerDao customers,
                       CashService cash, StockService stock, ActivityAudit journal) {
        this.sales = sales;
        this.items = items;
        this.payments = payments;
        this.returns = returns;
        this.products = products;
        this.units = units;
        this.customers = customers;
        this.cash = cash;
        this.stock = stock;
        this.journal = journal;
    }

    public static SaleService get() {
        return INSTANCE;
    }

    // -------------------------------------------------------------- types

    /** Ligne de panier transmise au moment de l'encaissement. */
    public record CheckoutLine(Product product, int quantity, Integer unitId, String imei,
                               BigDecimal unitPrice, BigDecimal lineDiscount, int warrantyMonths) {
    }

    /** Règlement : mode + montant effectivement remis. */
    public record PaymentLine(String method, BigDecimal amount) {
    }

    /** Demande d'encaissement complète. */
    public record CheckoutRequest(Integer customerId, List<CheckoutLine> lines,
                                  BigDecimal globalDiscount, List<PaymentLine> payments, String note) {
    }

    /** Résultat : vente persistée + rendu de monnaie. */
    public record CheckoutResult(Sale sale, BigDecimal change) {
    }

    /** Ligne à retourner : identifiant de ligne de vente + quantité. */
    public record RefundLine(int saleItemId, int quantity) {
    }

    /** Page de ventes (historique). */
    public record SalesPage(List<Sale> items, long total, int page, int size) {
        public int pageCount() {
            return size <= 0 ? 1 : (int) Math.max(1, (total + size - 1) / size);
        }
    }

    // ---------------------------------------------------------- encaissement

    /**
     * Enregistre une vente complète dans une transaction unique.
     *
     * @throws ValidationException si le panier est vide, si le stock est
     *                             insuffisant, si un IMEI est déjà vendu ou si
     *                             aucune session de caisse n'est ouverte
     */
    public CheckoutResult checkout(CheckoutRequest req) {
        PermissionService.get().check(PermissionDefaults.M_POS, PermissionService.Action.CREATE);
        if (req == null || req.lines() == null || req.lines().isEmpty()) {
            throw new ValidationException("Le panier est vide.");
        }
        // Le stockage et l'encaissement en espèces exigent une caisse ouverte.
        CashSession session = cash.requireOpen();
        int uid = userIdOrFallback();

        try {
            return Tx.run(() -> {
                Calcul calc = calculer(req);
                if (calc.due().signum() > 0 && req.customerId() == null) {
                    throw new ValidationException(
                            "Une vente avec un reste à payer nécessite un client enregistré.");
                }

                Sale sale = new Sale();
                sale.setNumber("V-" + String.format("%06d", sales.nextNumber()));
                sale.setSaleDate(LocalDateTime.now());
                sale.setCustomerId(req.customerId());
                sale.setUserId(uid);
                sale.setCashSessionId(session.getId());
                sale.setSubtotal(calc.subtotal());
                sale.setDiscount(calc.discount());
                sale.setTax(calc.tax());
                sale.setTotal(calc.total());
                sale.setPaidAmount(calc.applied());
                sale.setDueAmount(calc.due());
                sale.setProfit(calc.profit());
                sale.setPaymentStatus(calc.status());
                sale.setStatus(Sale.VALIDEE);
                sale.setNote(req.note());
                sales.insert(sale);

                // Lignes + mouvements de stock + statut des IMEI
                for (CheckoutLine ligne : req.lines()) {
                    Product produit = ligne.product();
                    if (produit == null) {
                        throw new ValidationException("Produit absent d'une ligne de vente.");
                    }
                    int qte = ligne.quantity();
                    if (qte <= 0) {
                        throw new ValidationException("Quantité invalide pour « " + produit.getName() + " ».");
                    }
                    BigDecimal prix = ligne.unitPrice() == null
                            ? produit.getSalePrice() : ligne.unitPrice();
                    BigDecimal remise = ligne.lineDiscount() == null
                            ? BigDecimal.ZERO : ligne.lineDiscount();
                    BigDecimal cout = produit.getAvgCost() == null
                            ? produit.getPurchasePrice() : produit.getAvgCost();

                    SaleItem item = new SaleItem();
                    item.setSaleId(sale.getId());
                    item.setProductId(produit.getId());
                    item.setProductName(produit.getName());
                    item.setProductRef(produit.getRef());
                    item.setDescription(produit.getName());
                    item.setQuantity(qte);
                    item.setUnitPrice(prix);
                    item.setUnitCost(cout);
                    item.setDiscount(remise);
                    item.setVatRate(produit.getVatRate());
                    item.setWarrantyMonths(ligne.warrantyMonths() > 0
                            ? ligne.warrantyMonths() : produit.getWarrantyMonths());
                    item.setImei(ligne.imei());
                    item.computeTotal();
                    items.insert(item);
                    sale.getItems().add(item);

                    if (produit.isTrackImei()) {
                        Integer unitId = ligne.unitId();
                        if (unitId == null) {
                            throw new ValidationException("IMEI obligatoire pour « "
                                    + produit.getName() + " ».");
                        }
                        ProductUnit unit = units.findById(unitId).orElseThrow(
                                () -> new ValidationException("Appareil introuvable (IMEI)."));
                        if (unit.productId() != produit.getId()) {
                            throw new ValidationException("L'IMEI saisi n'appartient pas à « "
                                    + produit.getName() + " ».");
                        }
                        if (!ProductUnit.EN_STOCK.equals(unit.status())) {
                            throw new ValidationException("L'IMEI " + unit.imei()
                                    + " est déjà vendu ou indisponible.");
                        }
                        item.setProductUnitId((int) unitId);
                        units.markSold(unitId, sale.getId(), item.getId());
                    }

                    // Mouvement de stock (réutilise StockService : contrôle du stock négatif)
                    stock.apply(produit.getId(), StockMovementType.VENTE, qte, ligne.unitId(), cout,
                            "Vente " + sale.getNumber(), "SALE", sale.getId());
                }

                // Règlements (répartis sur le montant réellement dû) + caisse
                repartirReglements(sale, req.payments(), calc, session, uid);

                // Dette client
                if (calc.due().signum() > 0 && req.customerId() != null) {
                    customers.updateBalance(req.customerId(), calc.due());
                }

                journal.log(uid, ActivityActions.VENTE, "sales", sale.getId(),
                        "Vente " + sale.getNumber() + " — " + Format.money(sale.getTotal())
                                + " (" + calc.status() + ")");
                sale.setUsername(SessionContext.get().userLabel());
                return new CheckoutResult(sale, calc.change());
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Enregistrement de la vente impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Calcule les totaux d'une demande d'encaissement. */
    private Calcul calculer(CheckoutRequest req) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal remisesLignes = BigDecimal.ZERO;
        BigDecimal taxe = BigDecimal.ZERO;
        BigDecimal marge = BigDecimal.ZERO;
        for (CheckoutLine ligne : req.lines()) {
            Product p = ligne.product();
            if (p == null) {
                continue;
            }
            BigDecimal prix = ligne.unitPrice() == null ? p.getSalePrice() : ligne.unitPrice();
            BigDecimal remise = ligne.lineDiscount() == null ? BigDecimal.ZERO : ligne.lineDiscount();
            BigDecimal cout = p.getAvgCost() == null ? p.getPurchasePrice() : p.getAvgCost();
            BigDecimal brut = prix.multiply(BigDecimal.valueOf(ligne.quantity()));
            BigDecimal net = brut.subtract(remise);
            if (net.signum() < 0) {
                net = BigDecimal.ZERO;
            }
            subtotal = subtotal.add(brut);
            remisesLignes = remisesLignes.add(remise);
            marge = marge.add(prix.subtract(cout).multiply(BigDecimal.valueOf(ligne.quantity()))
                    .subtract(remise));
            BigDecimal taux = p.getVatRate() == null ? BigDecimal.ZERO : p.getVatRate();
            if (taux.signum() > 0) {
                BigDecimal diviseur = BigDecimal.valueOf(100).add(taux);
                taxe = taxe.add(net.multiply(taux).divide(diviseur, 2, RoundingMode.HALF_UP));
            }
        }
        BigDecimal globale = req.globalDiscount() == null ? BigDecimal.ZERO : req.globalDiscount();
        if (globale.signum() < 0) {
            globale = BigDecimal.ZERO;
        }
        BigDecimal discount = remisesLignes.add(globale);
        BigDecimal total = subtotal.subtract(discount);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }
        BigDecimal profit = marge.subtract(globale);

        BigDecimal verse = BigDecimal.ZERO;
        if (req.payments() != null) {
            for (PaymentLine pl : req.payments()) {
                if (pl.amount() != null && pl.amount().signum() > 0) {
                    verse = verse.add(pl.amount());
                }
            }
        }
        BigDecimal applique = verse.min(total);
        BigDecimal rendu = verse.subtract(applique);
        BigDecimal du = total.subtract(applique);
        if (du.signum() < 0) {
            du = BigDecimal.ZERO;
        }
        String statut;
        if (applique.signum() <= 0 && du.signum() > 0) {
            statut = Sale.IMPAYEE;
        } else if (du.signum() > 0) {
            statut = Sale.PARTIELLE;
        } else {
            statut = Sale.PAYEE;
        }
        return new Calcul(scale(subtotal), scale(discount), scale(taxe), scale(total),
                scale(applique), scale(du), scale(profit), scale(rendu), statut);
    }

    private record Calcul(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total,
                          BigDecimal applied, BigDecimal due, BigDecimal profit, BigDecimal change,
                          String status) {
    }

    /** Enregistre les règlements (montants réels), la caisse et la dette. */
    private void repartirReglements(Sale sale, List<PaymentLine> lignes, Calcul calc,
                                    CashSession session, int uid) throws SQLException {
        BigDecimal restant = calc.applied();
        BigDecimal especes = BigDecimal.ZERO;
        if (lignes != null) {
            for (PaymentLine pl : lignes) {
                if (restant.signum() <= 0) {
                    break;
                }
                if (pl.amount() == null || pl.amount().signum() <= 0) {
                    continue;
                }
                String methode = normaliserMethode(pl.method());
                BigDecimal alloue = pl.amount().min(restant);
                restant = restant.subtract(alloue);

                SalePayment paiement = new SalePayment();
                paiement.setSaleId(sale.getId());
                paiement.setPaymentDate(LocalDateTime.now());
                paiement.setMethod(methode);
                paiement.setAmount(alloue);
                paiement.setCashSessionId(session == null ? null : session.getId());
                paiement.setUserId(uid);
                payments.insert(paiement);
                sale.getPayments().add(paiement);

                if (PaymentMethod.ESPECES.name().equals(methode)) {
                    especes = especes.add(alloue);
                }
            }
        }
        cash.recordSaleCash(session.getId(), sale.getId(), especes, uid);
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

    // ------------------------------------------------------------- retour

    /**
     * Retour / remboursement (partiel ou total) : remet le stock, ajuste la
     * dette du client et enregistre la sortie de caisse. Une seule transaction.
     */
    public SaleReturn refund(int saleId, List<RefundLine> lignes, String reason, String refundMethod) {
        PermissionService.get().check(PermissionDefaults.M_SALES, PermissionService.Action.VALIDATE);
        if (lignes == null || lignes.isEmpty()) {
            throw new ValidationException("Aucune ligne à retourner.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Le motif du retour est obligatoire.");
        }
        int uid = userIdOrFallback();
        String methode = normaliserMethode(refundMethod);
        try {
            return Tx.run(() -> {
                Sale sale = sales.byId(saleId).orElseThrow(
                        () -> new ValidationException("Vente introuvable."));
                if (Sale.ANNULEE.equals(sale.getStatus())) {
                    throw new ValidationException("Cette vente est annulée.");
                }
                List<SaleItem> articles = items.listBySale(saleId);
                Map<Integer, SaleItem> parId = new HashMap<>();
                for (SaleItem it : articles) {
                    parId.put(it.getId(), it);
                }

                BigDecimal totalRetour = BigDecimal.ZERO;
                List<SaleReturn.SaleReturnItem> retenues = new ArrayList<>();
                for (RefundLine rl : lignes) {
                    SaleItem it = parId.get(rl.saleItemId());
                    if (it == null) {
                        throw new ValidationException("Ligne de vente introuvable.");
                    }
                    int dispo = it.returnableQuantity();
                    if (rl.quantity() <= 0 || rl.quantity() > dispo) {
                        throw new ValidationException("Quantité à retourner invalide pour « "
                                + it.getProductName() + " » (max " + dispo + ").");
                    }
                    BigDecimal prixEffectif = it.getQuantity() <= 0 ? it.getUnitPrice()
                            : it.getTotal().divide(BigDecimal.valueOf(it.getQuantity()), 2,
                                    RoundingMode.HALF_UP);
                    BigDecimal ligneTotal = prixEffectif.multiply(BigDecimal.valueOf(rl.quantity()));
                    totalRetour = totalRetour.add(ligneTotal);

                    SaleReturn.SaleReturnItem ri = new SaleReturn.SaleReturnItem();
                    ri.setSaleItemId(it.getId());
                    ri.setProductName(it.getProductName());
                    ri.setQuantity(rl.quantity());
                    ri.setUnitPrice(prixEffectif);
                    ri.setTotal(ligneTotal);
                    retenues.add(ri);
                }

                CashSession session = null;
                if (PaymentMethod.ESPECES.name().equals(methode)) {
                    session = cash.currentSession().orElse(null);
                }

                SaleReturn retour = new SaleReturn();
                retour.setSaleId(saleId);
                retour.setReturnDate(LocalDateTime.now());
                retour.setTotal(scale(totalRetour));
                retour.setReason(reason);
                retour.setRefundMethod(methode);
                retour.setUserId(uid);
                retour.setCashSessionId(session == null ? null : session.getId());
                returns.insert(retour);

                for (int i = 0; i < retenues.size(); i++) {
                    SaleReturn.SaleReturnItem ri = retenues.get(i);
                    returns.insertItem(retour.getId(), ri.getSaleItemId(), ri.getQuantity(),
                            ri.getUnitPrice(), ri.getTotal(), ri.getProductName());
                    SaleItem it = parId.get(ri.getSaleItemId());
                    stock.apply(it.getProductId(), StockMovementType.RETOUR, ri.getQuantity(),
                            it.getProductUnitId(), it.getUnitCost(), "Retour " + retour.getNumber(),
                            "SALE_RETURN", retour.getId());
                    if (it.getProductUnitId() != null) {
                        units.updateStatus(it.getProductUnitId(), ProductUnit.EN_STOCK, null);
                    }
                }
                retour.setItems(retenues);

                // Dette d'abord, puis espèces
                BigDecimal dette = sale.getDueAmount();
                BigDecimal reductionDette = dette.min(totalRetour);
                BigDecimal rembourseEspeces = totalRetour.subtract(reductionDette);
                BigDecimal nouvelleDette = dette.subtract(reductionDette);
                BigDecimal nouveauPaye = sale.getPaidAmount().subtract(rembourseEspeces);
                if (nouveauPaye.signum() < 0) {
                    nouveauPaye = BigDecimal.ZERO;
                }
                if (reductionDette.signum() > 0 && sale.getCustomerId() != null) {
                    customers.updateBalance(sale.getCustomerId(), reductionDette.negate());
                }
                if (rembourseEspeces.signum() > 0 && session != null) {
                    cash.recordRefundCash(session.getId(), retour.getId(), rembourseEspeces, uid);
                }

                int vendues = 0;
                int rendues = 0;
                for (SaleItem it : articles) {
                    vendues += it.getQuantity();
                    rendues += it.getReturnedQuantity();
                }
                int renduesApres = rendues;
                for (SaleReturn.SaleReturnItem ri : retenues) {
                    renduesApres += ri.getQuantity();
                }
                String statut;
                if (renduesApres >= vendues) {
                    statut = Sale.REMBOURSEE;
                } else if (nouvelleDette.signum() > 0 && nouveauPaye.signum() <= 0) {
                    statut = Sale.IMPAYEE;
                } else if (nouvelleDette.signum() > 0) {
                    statut = Sale.PARTIELLE;
                } else {
                    statut = Sale.PAYEE;
                }
                sales.updateSettlement(saleId, scale(nouveauPaye), scale(nouvelleDette), statut);

                journal.log(uid, ActivityActions.REMBOURSEMENT, "sale_returns", retour.getId(),
                        "Retour " + retour.getNumber() + " sur " + sale.getNumber() + " — "
                                + Format.money(totalRetour) + " (" + reason + ")");
                retour.setUsername(SessionContext.get().userLabel());
                return retour;
            });
        } catch (ValidationException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Retour de vente impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // -------------------------------------------------------------- lectures

    /** Vente complète (en-tête + lignes + règlements + retours). */
    public Sale byId(int id) {
        try {
            Sale s = sales.byId(id).orElseThrow(() -> new ValidationException("Vente introuvable."));
            s.setItems(items.listBySale(id));
            s.setPayments(payments.listBySale(id));
            return s;
        } catch (SQLException e) {
            log.error("Lecture de la vente impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public List<SaleReturn> returnsOf(int saleId) {
        try {
            return returns.listBySale(saleId);
        } catch (SQLException e) {
            log.error("Lecture des retours impossible", e);
            return List.of();
        }
    }

    public SalesPage history(SaleDao.Filter filter, int page, int size) {
        int p = Math.max(1, page);
        int t = Math.max(1, size);
        try {
            long total = sales.count(filter);
            return new SalesPage(sales.search(filter, t, (p - 1) * t), total, p, t);
        } catch (SQLException e) {
            log.error("Lecture de l'historique des ventes impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public List<Sale> recent(int limit) {
        try {
            return sales.recent(limit);
        } catch (SQLException e) {
            log.error("Lecture des ventes récentes impossible", e);
            return List.of();
        }
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static int userIdOrFallback() {
        var u = SessionContext.get().getUser();
        return u == null ? 1 : u.getId();
    }
}
