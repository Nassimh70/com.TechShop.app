package com.techshop.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du hachage et de la robustesse des mots de passe (SPEC §1).
 */
class PasswordsTest {

    @Test
    @DisplayName("Un mot de passe haché se vérifie et ne fuit pas en clair")
    void hachageEtVerification() {
        String clair = "admin123";
        String hash = Passwords.hash(clair);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"));
        assertFalse(hash.contains(clair));
        assertTrue(Passwords.verify(clair, hash));
        assertFalse(Passwords.verify("mauvais", hash));
    }

    @Test
    @DisplayName("Deux hachages du même mot de passe diffèrent (sel aléatoire)")
    void selAleatoire() {
        String a = Passwords.hash("motdepasse1");
        String b = Passwords.hash("admin123");
        assertFalse(a.equals(b));
    }

    @Test
    @DisplayName("Une empreinte illisible est refusée sans exception")
    void empreinteInvalide() {
        assertFalse(Passwords.verify("x", "pas-un-hash"));
        assertFalse(Passwords.verify("x", null));
        assertFalse(Passwords.verify("x", ""));
    }

    @Test
    @DisplayName("La robustesse exige 8 caractères, une lettre et un chiffre")
    void validationRobustesse() {
        assertNotNull(Passwords.validate(null));
        assertNotNull(Passwords.validate("court1"));
        assertNotNull(Passwords.validate("motdepasse"));      // pas de chiffre
        assertNotNull(Passwords.validate("12345678"));
        assertNull(Passwords.validate("admin123"));
        assertNull(Passwords.validate("MotDePasse9"));
    }

    @Test
    @DisplayName("Le mot de passe temporaire est robuste et aléatoire")
    void motDePasseTemporaire() {
        String a = Passwords.temporary();
        String b = Passwords.temporary();
        assertNull(Passwords.validate(a));
        assertTrue(a.length() >= Passwords.LONGUEUR_MIN);
        assertFalse(a.equals(b));
    }
}
