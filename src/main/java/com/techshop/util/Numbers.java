package com.techshop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Analyse des saisies numériques françaises (« 12 500,50 »). Centralise la
 * conversion texte → montant / entier utilisée par les écrans de vente et de
 * caisse.
 */
public final class Numbers {

    private Numbers() {
    }

    /** Montant à deux décimales ; 0 si la saisie est vide. */
    public static BigDecimal money(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(valeur.replace(",", ".").replace(" ", "")
                    .replace("\u00A0", "").trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new ValidationException("Montant invalide : « " + valeur + " ».");
        }
    }

    /** Montant sans contrainte d'échelle (pour les saisies internes). */
    public static BigDecimal decimal(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(valeur.replace(",", ".").replace(" ", "")
                    .replace("\u00A0", "").trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Nombre invalide : « " + valeur + " ».");
        }
    }

    /** Entier ; {@code defaut} si la saisie est vide. */
    public static int entier(String valeur, int defaut) {
        if (valeur == null || valeur.isBlank()) {
            return defaut;
        }
        try {
            return (int) Math.round(Double.parseDouble(valeur.replace(",", ".").trim()));
        } catch (NumberFormatException e) {
            throw new ValidationException("Nombre entier invalide : « " + valeur + " ».");
        }
    }

    /** Entier à partir d'une quantité comptée (jamais négatif). */
    public static int compte(String valeur) {
        return Math.max(0, entier(valeur, 0));
    }
}
