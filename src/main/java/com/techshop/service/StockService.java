package com.techshop.service;

import com.techshop.config.Tx;
import com.techshop.dao.ProductDao;
import com.techshop.dao.ProductUnitDao;
import com.techshop.dao.StockMovementDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.Product;
import com.techshop.model.ProductUnit;
import com.techshop.model.StockMovement;
import com.techshop.model.StockMovementType;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service central des mouvements de stock (SPEC §4.6, §6.2). TOUTE variation du
 * stock passe par ici : entrée, sortie, ajustement, perte, vente, retour,
 * réparation, inventaire. Chaque mouvement est écrit dans « stock_movements »
 * et la quantité du produit est mise à jour dans la même transaction, ce qui
 * garantit un stock courant toujours cohérent et jamais négatif.
 */
public final class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private static final StockService INSTANCE = new StockService(
            new ProductDao(), new StockMovementDao(), new ProductUnitDao(),
            ActivityLogService.get());

    private final ProductDao products;
    private final StockMovementDao movements;
    private final ProductUnitDao units;
    private final ActivityAudit journal;

    public StockService(ProductDao products, StockMovementDao movements, ProductUnitDao units,
                        ActivityAudit journal) {
        this.products = products;
        this.movements = movements;
        this.units = units;
        this.journal = journal;
    }

    public static StockService get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ types

    /** Synthèse du stock (cartes du haut de l'écran Stock). */
    public record Summary(java.math.BigDecimal value, long references, long low, long out) {
    }

    /** Ligne d'inventaire : produit + quantité comptée physiquement. */
    public record InventoryLine(Product product, int counted) {

        /** Écart = compté - système (positif : excédent, négatif : manquant). */
        public int gap() {
            return counted - product.getQuantity();
        }
    }

    /** Page d'historique des mouvements. */
    public record History(java.util.List<StockMovement> items, long total, int page, int size) {

        public int pageCount() {
            return size <= 0 ? 1 : (int) Math.max(1, (total + size - 1) / size);
        }
    }

    // -------------------------------------------------- mouvement de bas niveau

    /**
     * Applique un mouvement (sans contrôle de droit : réservé aux autres services
     * et à la vente). Le signe est déduit du type, sauf AJUSTEMENT/INVENTAIRE où
     * la quantité porte déjà son signe. Le stock ne peut jamais devenir négatif.
     */
    public StockMovement apply(int productId, StockMovementType type, int quantity, Integer unitId,
                               java.math.BigDecimal unitCost, String reason, String referenceType,
                               Integer referenceId) {
        if (type == null) {
            throw new ValidationException("Type de mouvement manquant.");
        }
        int delta = type.signeVariable() ? quantity : type.signe() * Math.abs(quantity);
        try {
            return Tx.run(() -> applyDelta(productId, type, delta, unitId, unitCost, reason,
                    referenceType, referenceId));
        } catch (SQLException e) {
            log.error("Mouvement de stock impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ------------------------------------------------- points d'entrée publics

    /** Entrée de stock (réception, retour fournisseur...). Droit : créer. */
    public StockMovement receive(int productId, int quantity, java.math.BigDecimal unitCost,
                                 String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.CREATE);
        return apply(productId, StockMovementType.ENTREE, Math.abs(quantity), null, unitCost,
                reason, "MANUAL", null);
    }

    /** Sortie de stock (perte, casse, usage interne). Droit : modifier. Motif requis. */
    public StockMovement loss(int productId, int quantity, String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        exigerMotif(reason);
        return apply(productId, StockMovementType.PERTE, Math.abs(quantity), null, null,
                reason, "MANUAL", null);
    }

    /** Sortie de stock générique. Droit : modifier. Motif requis. */
    public StockMovement issue(int productId, int quantity, String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        exigerMotif(reason);
        return apply(productId, StockMovementType.SORTIE, Math.abs(quantity), null, null,
                reason, "MANUAL", null);
    }

    /** Retour en stock (retour client, annulation). Droit : modifier. Motif requis. */
    public StockMovement returnStock(int productId, int quantity, String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        exigerMotif(reason);
        return apply(productId, StockMovementType.RETOUR, Math.abs(quantity), null, null,
                reason, "MANUAL", null);
    }

    /** Sortie liée à une réparation (pièce consommée). Droit : modifier. */
    public StockMovement repairIssue(int productId, int quantity, Integer repairId, String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        exigerMotif(reason);
        return apply(productId, StockMovementType.REPARATION, Math.abs(quantity), null, null,
                reason, "REPAIR", repairId);
    }

    /** Sortie liée à une vente (appelée par le module Caisse). Droit : modifier. */
    public StockMovement saleIssue(int productId, int quantity, Integer unitId, Integer saleId,
                                   java.math.BigDecimal unitCost) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        return apply(productId, StockMovementType.VENTE, Math.abs(quantity), unitId, unitCost,
                "Vente", "SALE", saleId);
    }

    /**
     * Ajustement manuel : la nouvelle quantité devient la référence.
     * Droit : modifier. Motif obligatoire.
     */
    public StockMovement adjust(int productId, int newQuantity, String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        exigerMotif(reason);
        if (newQuantity < 0) {
            throw new ValidationException("La quantité ne peut pas être négative.");
        }
        try {
            return Tx.run(() -> {
                Product p = products.lockForUpdate(productId).orElseThrow(
                        () -> new ValidationException("Produit introuvable."));
                int delta = newQuantity - p.getQuantity();
                if (delta == 0) {
                    return null;
                }
                return persist(p, StockMovementType.AJUSTEMENT, delta, null, null, reason,
                        "MANUAL", null);
            });
        } catch (SQLException e) {
            log.error("Ajustement de stock impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /**
     * Valide un inventaire : génère un ajustement (type INVENTAIRE) pour chaque
     * écart constaté, dans une seule transaction. Droit : valider. Motif requis.
     *
     * @return le nombre d'ajustements générés
     */
    public int applyInventory(java.util.List<InventoryLine> lignes, String reason) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.VALIDATE);
        exigerMotif(reason);
        if (lignes == null || lignes.isEmpty()) {
            throw new ValidationException("Aucune ligne d'inventaire à valider.");
        }
        try {
            return Tx.run(() -> {
                int ajustes = 0;
                for (InventoryLine ligne : lignes) {
                    Product p = products.lockForUpdate(ligne.product().getId()).orElseThrow(
                            () -> new ValidationException("Produit introuvable."));
                    int ecart = ligne.counted() - p.getQuantity();
                    if (ecart != 0) {
                        persist(p, StockMovementType.INVENTAIRE, ecart, null, null, reason,
                                "INVENTORY", null);
                        ajustes++;
                    }
                }
                if (ajustes > 0) {
                    journal.log(userId(), ActivityActions.STOCK_AJUSTEMENT, "stock", null,
                            "Inventaire validé : " + ajustes + " ajustement(s) — " + reason);
                }
                return ajustes;
            });
        } catch (SQLException e) {
            log.error("Validation d'inventaire impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ---------------------------------------------------------------- lectures

    public Summary summary() {
        try {
            return new Summary(products.stockValueAtCost(), products.countActive(),
                    products.countLowStock(), products.countOutOfStock());
        } catch (SQLException e) {
            log.error("Lecture de la synthèse du stock impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Produits en alerte (rupture ou stock ≤ seuil), pour l'écran et la cloche. */
    public java.util.List<Product> alerts() {
        try {
            return products.alerts();
        } catch (SQLException e) {
            log.error("Lecture des alertes de stock impossible", e);
            return java.util.List.of();
        }
    }

    public History history(StockMovementDao.Filter filter, int page, int size) {
        int p = Math.max(1, page);
        int t = Math.max(1, size);
        try {
            long total = movements.count(filter);
            return new History(movements.search(filter, t, (p - 1) * t), total, p, t);
        } catch (SQLException e) {
            log.error("Lecture de l'historique des mouvements impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public java.util.List<StockMovement> recentMovements(int limit) {
        try {
            return movements.recent(limit);
        } catch (SQLException e) {
            log.error("Lecture des mouvements récents impossible", e);
            return java.util.List.of();
        }
    }

    public java.util.List<ProductUnit> units(ProductUnitDao.Filter filter) {
        try {
            return units.search(filter);
        } catch (SQLException e) {
            log.error("Lecture des appareils (IMEI) impossible", e);
            return java.util.List.of();
        }
    }

    /** Change le statut d'un appareil suivi (vente, réparation, retour...). */
    public void setUnitStatus(long unitId, String status) {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.EDIT);
        try {
            units.updateStatus(unitId, status, null);
            journal.log(userId(), ActivityActions.MODIFICATION, "product_units", (int) unitId,
                    "Statut de l'appareil → " + ProductUnit.libelle(status));
        } catch (SQLException e) {
            log.error("Changement de statut d'appareil impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ----------------------------------------------------------------- interne

    private StockMovement applyDelta(int productId, StockMovementType type, int delta,
                                     Integer unitId, java.math.BigDecimal unitCost, String reason,
                                     String referenceType, Integer referenceId) throws SQLException {
        Product p = products.lockForUpdate(productId).orElseThrow(
                () -> new ValidationException("Produit introuvable."));
        return persist(p, type, delta, unitId, unitCost, reason, referenceType, referenceId);
    }

    private StockMovement persist(Product p, StockMovementType type, int delta, Integer unitId,
                                  java.math.BigDecimal unitCost, String reason, String referenceType,
                                  Integer referenceId) throws SQLException {
        int avant = p.getQuantity();
        int apres = avant + delta;
        if (apres < 0) {
            throw new ValidationException("Stock insuffisant : " + avant
                    + " unité(s) disponible(s) pour « " + p.getName() + " ».");
        }
        java.math.BigDecimal cout = unitCost != null ? unitCost : p.getAvgCost();
        long id = movements.insert(p.getId(), unitId, type.name(), delta, avant, apres, cout,
                normaliserMotif(reason), referenceType, referenceId, userId(), java.time.LocalDateTime.now());
        products.setQuantity(p.getId(), apres);
        journal.log(userId(), ActivityActions.STOCK_MOUVEMENT, "stock_movements", (int) id,
                type.getLabel() + " " + (delta > 0 ? "+" : "") + delta + " — " + p.getName()
                        + (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""));
        return new StockMovement(id, p.getId(), p.getRef(), p.getName(), unitId, null, type.name(),
                delta, avant, apres, cout, reason, referenceType, referenceId, userId(), null,
                java.time.LocalDateTime.now(), p.getBarcode());
    }

    private static String normaliserMotif(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private static void exigerMotif(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason", "Le motif est obligatoire.");
        }
    }

    private static Integer userId() {
        var u = SessionContext.get().getUser();
        return u == null ? null : u.getId();
    }
}
