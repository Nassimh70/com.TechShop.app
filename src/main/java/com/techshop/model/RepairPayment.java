package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Acompte ou règlement d'un dossier de réparation (table repair_payments).
 * Un règlement en espèces est lié à la session de caisse ouverte.
 */
public record RepairPayment(int id, int repairId, LocalDateTime paymentDate, String method,
                            BigDecimal amount, Integer cashSessionId, String note, Integer userId) {

    public String methodLabel() {
        return PaymentMethod.libelle(method);
    }
}
