package com.techshop.model;

/**
 * Nature d'un mouvement de stock (colonne stock_movements.type).
 * Le signe indique le sens par défaut du mouvement ; AJUSTEMENT et INVENTAIRE
 * peuvent être positifs ou négatifs (le service impose le signe de la quantité).
 */
public enum StockMovementType {

    ENTREE("Entrée", 1),
    SORTIE("Sortie", -1),
    AJUSTEMENT("Ajustement", 1),
    PERTE("Perte", -1),
    VENTE("Vente", -1),
    RETOUR("Retour", 1),
    REPARATION("Réparation", -1),
    INVENTAIRE("Inventaire", 1);

    private final String label;
    private final int signe;

    StockMovementType(String label, int signe) {
        this.label = label;
        this.signe = signe;
    }

    public String getLabel() {
        return label;
    }

    /** Sens par défaut : +1 entrée, -1 sortie. */
    public int signe() {
        return signe;
    }

    /** Ajustement / inventaire : la quantité porte déjà son signe. */
    public boolean signeVariable() {
        return this == AJUSTEMENT || this == INVENTAIRE;
    }

    /** Libellé français d'un code stocké en base. */
    public static String libelle(String code) {
        if (code == null) {
            return "—";
        }
        for (StockMovementType t : values()) {
            if (t.name().equalsIgnoreCase(code)) {
                return t.label;
            }
        }
        return code;
    }
}