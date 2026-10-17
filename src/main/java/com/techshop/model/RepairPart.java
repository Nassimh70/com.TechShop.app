package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pièce utilisée sur un dossier de réparation (table repair_parts).
 * La sortie de stock correspondante est tracée par {@code stockMovementId}.
 */
public record RepairPart(int id, int repairId, int productId, String productName,
                         Integer productUnitId, int quantity, BigDecimal unitCost,
                         BigDecimal unitPrice, BigDecimal total, Long stockMovementId,
                         LocalDateTime addedAt, Integer userId) {
}
