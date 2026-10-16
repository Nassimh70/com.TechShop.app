package com.techshop.model;

/**
 * État du stock d'un produit (SPEC §4.5, §4.6).
 * Déterminé à partir de la quantité courante et du seuil d'alerte :
 * rupture (0), stock faible (≤ seuil), disponible.
 */
public enum StockStatus {

    OK("En stock", "badge-success"),
    LOW("Stock faible", "badge-warning"),
    OUT("Rupture", "badge-danger");

    private final String label;
    private final String badgeCss;

    StockStatus(String label, String badgeCss) {
        this.label = label;
        this.badgeCss = badgeCss;
    }

    public String getLabel() {
        return label;
    }

    public String css() {
        return badgeCss;
    }

    /** Statut correspondant à une quantité et un seuil d'alerte. */
    public static StockStatus of(int quantity, int alertThreshold) {
        if (quantity <= 0) {
            return OUT;
        }
        if (quantity <= alertThreshold) {
            return LOW;
        }
        return OK;
    }
}