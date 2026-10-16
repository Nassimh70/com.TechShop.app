package com.techshop.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests du formatage (devise DA, dates, normalisation de recherche). */
class FormatTest {

    private static final char NBSP = '\u00A0';

    @Test
    @DisplayName("Montant : « 12 500,00 DA » avec espaces insécables et virgule décimale")
    void montantAuFormatDinar() {
        assertEquals("12" + NBSP + "500,00" + NBSP + "DA",
                Format.money(new BigDecimal("12500")));
    }

    @Test
    @DisplayName("Montant : arrondi à 2 décimales et séparateur de milliers")
    void montantArrondi() {
        assertEquals("987" + NBSP + "654,50" + NBSP + "DA",
                Format.money(new BigDecimal("987654.5")));
        assertEquals("0,00" + NBSP + "DA", Format.money(null));
    }

    @Test
    @DisplayName("Dates au format JJ/MM/AAAA")
    void datesFrancaises() {
        assertEquals("16/10/2026", Format.date(LocalDate.of(2026, 10, 16)));
        assertEquals("—", Format.date(null));
    }

    @Test
    @DisplayName("Normalisation de recherche : accents et majuscules ignorés")
    void normalisationRecherche() {
        assertEquals("ecran oled", Format.normalize("Écran OLED"));
        assertEquals("galaxy a15", Format.normalize("  Galaxy  A15 "));
    }

    @Test
    @DisplayName("Initiales pour les avatars")
    void initiales() {
        assertEquals("NB", Format.initials("Nadia Belkacem"));
        assertEquals("M", Format.initials("Mounir"));
        assertEquals("?", Format.initials(""));
    }
}
