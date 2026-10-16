package com.techshop.service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Politique de connexion (SPEC §4.1) : blocage temporaire du compte après
 * 5 échecs consécutifs. Logique pure, sans accès base, pour être testable.
 */
public final class LoginPolicy {

    /** Nombre d'échecs consécutifs déclenchant le blocage. */
    public static final int MAX_ECHECS = 5;

    /** Durée du blocage temporaire. */
    public static final Duration DUREE_BLOCAGE = Duration.ofMinutes(15);

    private LoginPolicy() {
    }

    /** Le compte est-il bloqué à l'instant donné ? */
    public static boolean estBloque(LocalDateTime lockedUntil, LocalDateTime maintenant) {
        return lockedUntil != null && lockedUntil.isAfter(maintenant);
    }

    /** Minutes restantes avant la fin du blocage (arrondi supérieur, min 1). */
    public static long minutesRestantes(LocalDateTime lockedUntil, LocalDateTime maintenant) {
        if (!estBloque(lockedUntil, maintenant)) {
            return 0;
        }
        long secondes = Duration.between(maintenant, lockedUntil).getSeconds();
        return Math.max(1, (secondes + 59) / 60);
    }

    /** Le prochain échec (portant le total à {@code totalEchecs}) doit-il bloquer ? */
    public static boolean doitBloquer(int totalEchecs) {
        return totalEchecs >= MAX_ECHECS;
    }

    /** Instant de fin de blocage pour un échec survenu maintenant. */
    public static LocalDateTime finBlocage(LocalDateTime maintenant) {
        return maintenant.plus(DUREE_BLOCAGE);
    }

    /** Tentatives restantes avant blocage, pour un nombre d'échecs donné. */
    public static int tentativesRestantes(int echecs) {
        return Math.max(0, MAX_ECHECS - echecs);
    }
}
