package com.techshop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatage des affichages : dinar algérien « 12 500,00 DA », dates JJ/MM/AAAA,
 * nombres. Unique endroit où les formats sont définis (SPEC §1).
 */
public final class Format {

    /** Espace insécable fine (pour les milliers, esprit « 12 500,00 DA »). */
    private static final char NBSP = '\u00A0';

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale FR = Locale.FRANCE;

    private Format() {
    }

    /** Montant monétaire : « 12 500,00 DA ». Chiffres alignés à droite côté UI. */
    public static String money(BigDecimal value) {
        if (value == null) {
            return "0,00" + NBSP + "DA";
        }
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP);
        String s = String.format(FR, "%,.2f", v).replace('\u202F', NBSP).replace(' ', NBSP);
        return s + NBSP + "DA";
    }

    /** Montant sans devise (colonnes de tableau compactes). */
    public static String moneyShort(BigDecimal value) {
        if (value == null) {
            return "0,00";
        }
        return String.format(FR, "%,.2f", value.setScale(2, RoundingMode.HALF_UP))
                .replace('\u202F', NBSP).replace(' ', NBSP);
    }

    /** Entier avec séparateur de milliers : « 1 250 ». */
    public static long count(long value) {
        return value;
    }

    /** Nombre entier formaté. */
    public static String intStr(long value) {
        return String.format(FR, "%d", value).replace(' ', NBSP);
    }

    /** Pourcentage : « 18,5 % ». */
    public static String percent(BigDecimal value) {
        if (value == null) {
            return "0 %";
        }
        return String.format(FR, "%.1f", value).replace(' ', NBSP) + "\u00A0%";
    }

    /** Date JJ/MM/AAAA. */
    public static String date(LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }

    /** Date + heure JJ/MM/AAAA HH:mm. */
    public static String dateTime(LocalDateTime d) {
        return d == null ? "—" : DATE_TIME.format(d);
    }

    /**
     * Normalisation pour la recherche : minuscules, sans accents ni caractères
     * spéciaux (« Écran OLED » -> « ecran oled »).
     */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        String cleaned = n.replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ").toLowerCase(FR).trim();
        return cleaned;
    }

    /** Initiales d'un nom (« Nadia Belkacem » -> « NB »), 1 à 2 caractères. */
    public static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(FR);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(FR);
    }

    /** Troncature élégante pour les libellés serrés. */
    public static String ellipsis(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
