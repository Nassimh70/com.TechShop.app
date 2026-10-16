package com.techshop.util;

/**
 * Erreur de validation métier (SPEC §6) : message destiné à l'utilisateur,
 * affiché sous le champ concerné ou dans un toast. Les contrôleurs n'attrapent
 * que ce type pour signaler une saisie invalide.
 */
public class ValidationException extends RuntimeException {

    /** Champ concerné (identifiant de formulaire), facultatif. */
    private final String champ;

    public ValidationException(String message) {
        this(null, message);
    }

    public ValidationException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
