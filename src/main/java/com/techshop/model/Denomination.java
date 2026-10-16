package com.techshop.model;

import java.math.BigDecimal;

/**
 * Coupure (billet ou pièce) utilisée lors du comptage de clôture de caisse.
 * Les valeurs sont celles en circulation en Algérie (dinar).
 */
public record Denomination(String label, BigDecimal value, boolean billet) {

    /** Billets et pièces en circulation (du plus grand au plus petit). */
    public static final java.util.List<Denomination> DZD = java.util.List.of(
            new Denomination("2 000 DA", new BigDecimal("2000"), true),
            new Denomination("1 000 DA", new BigDecimal("1000"), true),
            new Denomination("500 DA", new BigDecimal("500"), true),
            new Denomination("200 DA", new BigDecimal("200"), true),
            new Denomination("100 DA", new BigDecimal("100"), true),
            new Denomination("50 DA", new BigDecimal("50"), false),
            new Denomination("20 DA", new BigDecimal("20"), false),
            new Denomination("10 DA", new BigDecimal("10"), false),
            new Denomination("5 DA", new BigDecimal("5"), false),
            new Denomination("2 DA", new BigDecimal("2"), false),
            new Denomination("1 DA", new BigDecimal("1"), false));
}
