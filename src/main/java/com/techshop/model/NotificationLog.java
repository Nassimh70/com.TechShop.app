package com.techshop.model;

import java.time.LocalDateTime;

/**
 * Notification client journalisée (table notifications_log). L'envoi est simulé
 * (SPEC §4.9) : le message est conservé avec le statut SIMULE.
 */
public record NotificationLog(long id, String channel, Integer customerId, Integer repairId,
                              Integer saleId, String recipient, String message, String status,
                              LocalDateTime createdAt, Integer userId) {

    public static final String SIMULE = "SIMULE";

    public String channelLabel() {
        if (channel == null) {
            return "—";
        }
        return switch (channel) {
            case "SMS" -> "SMS";
            case "APPEL" -> "Appel";
            case "EMAIL" -> "Email";
            case "INTERNE" -> "Interne";
            default -> channel;
        };
    }
}
