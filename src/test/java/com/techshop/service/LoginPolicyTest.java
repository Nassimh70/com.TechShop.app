package com.techshop.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la politique de blocage (SPEC §4.1) : blocage temporaire après
 * cinq échecs. Logique pure, sans base.
 */
class LoginPolicyTest {

    private final LocalDateTime maintenant = LocalDateTime.of(2026, 1, 15, 10, 0);

    @Test
    @DisplayName("Cinq échecs déclenchent le blocage")
    void cinqEchecs() {
        assertEquals(5, LoginPolicy.MAX_ECHECS);
        assertFalse(LoginPolicy.estBloque(null, maintenant));
        assertEquals(4, LoginPolicy.tentativesRestantes(1));
        assertEquals(0, LoginPolicy.tentativesRestantes(5));
    }

    @Test
    @DisplayName("Un compte est bloqué jusqu'à la fin du délai")
    void blocageTemporaire() {
        LocalDateTime fin = LoginPolicy.finBlocage(maintenant);
        assertEquals(maintenant.plusMinutes(15), fin);
        assertTrue(LoginPolicy.estBloque(fin, maintenant));
        assertEquals(15, LoginPolicy.minutesRestantes(fin, maintenant));
        // Après l'échéance, le blocage est levé
        assertFalse(LoginPolicy.estBloque(fin, fin.plusMinutes(1)));
        assertEquals(0, LoginPolicy.minutesRestantes(fin, fin.plusMinutes(1)));
    }

    @Test
    @DisplayName("Le blocage arrondit les minutes restantes au supérieur")
    void arrondiMinutes() {
        LocalDateTime fin = maintenant.plusSeconds(30);
        assertEquals(1, LoginPolicy.minutesRestantes(fin, maintenant));
    }
}
