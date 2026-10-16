package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mouvement de stock (SPEC §5, §6.2). Toute variation du stock passe par une
 * ligne de cette table : entrée, sortie, ajustement, perte, vente, retour,
 * réparation ou inventaire. La quantité porte le signe du mouvement.
 */
public record StockMovement(long id, int productId, String productRef, String productName,
                            Integer productUnitId, String unitImei, String type, int quantity,
                            int quantityBefore, int quantityAfter, BigDecimal unitCost,
                            String reason, String referenceType, Integer referenceId,
                            Integer userId, String username, LocalDateTime movementDate,
                            String barcode) {

    /** Libellé du type de mouvement. */
    public String typeLabel() {
        return StockMovementType.libelle(type);
    }

    /** Quantité signée avec le signe explicite (+/-) pour l'affichage. */
    public String quantityLabel() {
        return (quantity > 0 ? "+" : "") + quantity;
    }
}