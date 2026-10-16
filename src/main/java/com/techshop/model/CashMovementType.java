package com.techshop.model;

/** Sens d'un mouvement de caisse (colonne cash_movements.type). */
public enum CashMovementType {

    ENTREE("Entrée", 1),
    SORTIE("Sortie", -1);

    private final String label;
    private final int signe;

    CashMovementType(String label, int signe) {
        this.label = label;
        this.signe = signe;
    }

    public String getLabel() {
        return label;
    }

    public int signe() {
        return signe;
    }
}
