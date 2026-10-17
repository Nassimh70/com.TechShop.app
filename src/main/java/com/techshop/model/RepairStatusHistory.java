package com.techshop.model;

import java.time.LocalDateTime;

/**
 * Étape de la chronologie d'un dossier de réparation (table
 * repair_status_history) : « qui a changé quoi, quand ».
 */
public record RepairStatusHistory(long id, int repairId, String oldStatus, String newStatus,
                                  String comment, Integer userId, String userName,
                                  LocalDateTime changedAt) {

    public String newStatusLabel() {
        return RepairStatus.label(newStatus);
    }

    public String oldStatusLabel() {
        return oldStatus == null || oldStatus.isBlank() ? "—" : RepairStatus.label(oldStatus);
    }
}
