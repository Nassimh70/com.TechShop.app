package com.techshop.model;

import java.time.LocalDateTime;

/**
 * Utilisateur de l'application (SPEC §3, §5). Porte les informations de
 * sécurité nécessaires à l'authentification : empreinte BCrypt, indicateur de
 * changement obligatoire, compteur d'échecs et fin de blocage temporaire.
 * L'empreinte n'est jamais affichée dans l'interface.
 */
public class User {

    private final int id;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final String phone;
    private final String email;
    private final Role role;
    private final boolean active;
    private final boolean mustChangePassword;
    private final int failedAttempts;
    private final LocalDateTime lockedUntil;
    private final LocalDateTime lastLogin;

    /** Constructeur complet (couche données). */
    public User(int id, String username, String passwordHash, String fullName, String phone,
                String email, Role role, boolean active, boolean mustChangePassword,
                int failedAttempts, LocalDateTime lockedUntil, LocalDateTime lastLogin) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.role = role;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLogin = lastLogin;
    }

    /** Constructeur réduit (affichage, aperçu, tests). */
    public User(int id, String username, String fullName, Role role, boolean active) {
        this(id, username, null, fullName, null, null, role, active, false, 0, null, null);
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    @Override
    public String toString() {
        return fullName + " (" + role.getLabel() + ")";
    }
}
