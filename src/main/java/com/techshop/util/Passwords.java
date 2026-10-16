package com.techshop.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Hachage et vérification des mots de passe (SPEC §1 : jBCrypt).
 * Le coût BCrypt est fixé à 10 (compromis sécurité / rapidité pour un poste
 * de caisse). Aucun mot de passe n'est jamais stocké en clair.
 */
public final class Passwords {

    /** Coût BCrypt (log2 du nombre de tours). */
    public static final int COUT = 10;

    /** Longueur minimale d'un mot de passe. */
    public static final int LONGUEUR_MIN = 8;

    private Passwords() {
    }

    /** Hache un mot de passe en clair (jamais null). */
    public static String hash(String clair) {
        if (clair == null || clair.isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide");
        }
        return BCrypt.hashpw(clair, BCrypt.gensalt(COUT));
    }

    /** Vérifie un mot de passe en clair contre une empreinte BCrypt. */
    public static boolean verify(String clair, String hash) {
        if (clair == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(clair, hash);
        } catch (IllegalArgumentException e) {
            // Empreinte illisible (format inattendu) : on refuse par sécurité
            return false;
        }
    }

    /**
     * Valide la robustesse d'un mot de passe.
     *
     * @return un message d'erreur, ou null si le mot de passe est acceptable
     */
    public static String validate(String clair) {
        if (clair == null || clair.length() < LONGUEUR_MIN) {
            return "Le mot de passe doit contenir au moins " + LONGUEUR_MIN + " caractères.";
        }
        boolean lettre = clair.chars().anyMatch(Character::isLetter);
        boolean chiffre = clair.chars().anyMatch(Character::isDigit);
        if (!lettre || !chiffre) {
            return "Le mot de passe doit contenir au moins une lettre et un chiffre.";
        }
        return null;
    }

    /** Génère un mot de passe temporaire lisible (réinitialisation par un admin). */
    public static String temporary() {
        String lettres = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String minuscules = "abcdefghijkmnpqrstuvwxyz";
        String chiffres = "23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("Tech");
        sb.append(lettres.charAt(rnd.nextInt(lettres.length())));
        sb.append(minuscules.charAt(rnd.nextInt(minuscules.length())));
        for (int i = 0; i < 4; i++) {
            sb.append(chiffres.charAt(rnd.nextInt(chiffres.length())));
        }
        return sb.toString();
    }
}
