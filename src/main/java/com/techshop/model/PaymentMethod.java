package com.techshop.model;

/**
 * Mode de paiement d'une vente (colonne sale_payments.method).
 * CREDIT ne correspond pas à un encaissement : c'est la part laissée en dette
 * sur le compte du client (aucune ligne sale_payments n'est écrite pour elle).
 */
public enum PaymentMethod {

    ESPECES("Espèces"),
    CARTE("Carte"),
    VIREMENT("Virement"),
    CREDIT("Crédit");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Libellé d'un code stocké (tolérant : « ? » pour une valeur inconnue). */
    public static String libelle(String code) {
        if (code == null || code.isBlank()) {
            return "—";
        }
        for (PaymentMethod m : values()) {
            if (m.name().equals(code)) {
                return m.label;
            }
        }
        return code;
    }
}
