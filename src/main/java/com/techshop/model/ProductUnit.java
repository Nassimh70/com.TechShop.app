package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unité sérialisée d'un produit suivi par IMEI (SPEC §5, §6) : un appareil
 * physique, identifié par son IMEI ou numéro de série, avec son statut.
 */
public record ProductUnit(long id, int productId, String productName, String imei,
                          String serialNumber, String status, BigDecimal purchasePrice,
                          Integer saleId, LocalDateTime receivedAt, LocalDateTime soldAt) {

    /** Statuts d'un appareil (colonne product_units.status). */
    public static final String EN_STOCK = "EN_STOCK";
    public static final String VENDU = "VENDU";
    public static final String EN_REPARATION = "EN_REPARATION";
    public static final String RETOURNE = "RETOURNE";
    public static final String PERDU = "PERDU";

    /** Libellé français prêt à l'affichage. */
    public String statusLabel() {
        return libelle(status);
    }

    /** Classe CSS du badge correspondant au statut. */
    public String statusCss() {
        return switch (status == null ? "" : status) {
            case EN_STOCK -> "badge-success";
            case VENDU -> "badge-info";
            case EN_REPARATION -> "badge-warning";
            case RETOURNE -> "badge-neutral";
            case PERDU -> "badge-danger";
            default -> "badge-neutral";
        };
    }

    public static String libelle(String statut) {
        if (statut == null) {
            return "—";
        }
        return switch (statut) {
            case EN_STOCK -> "En stock";
            case VENDU -> "Vendu";
            case EN_REPARATION -> "En réparation";
            case RETOURNE -> "Retourné";
            case PERDU -> "Perdu";
            default -> statut;
        };
    }
}
