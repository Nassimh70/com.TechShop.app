package com.techshop.service;

import com.techshop.model.Role;
import com.techshop.model.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Session courante : utilisateur connecté.
 *
 * <p>Le module Utilisateurs et les rôles ont été retirés. {@link #getRole()}
 * renvoie le profil unique « Gérant » afin de conserver l'affichage de
 * l'en-tête et les signatures historiques.</p>
 */
public final class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();

    private final ObjectProperty<User> user = new SimpleObjectProperty<>();

    private SessionContext() {
    }

    public static SessionContext get() {
        return INSTANCE;
    }

    // --- Utilisateur ---
    public void setUser(User u) {
        user.set(u);
    }

    /** Déconnexion : vide l'utilisateur courant. */
    public void logout() {
        user.set(null);
    }

    public User getUser() {
        return user.get();
    }

    public ObjectProperty<User> userProperty() {
        return user;
    }

    public String userLabel() {
        User u = user.get();
        return u == null ? "—" : u.getFullName();
    }

    // --- Profil unique ---
    public Role getRole() {
        return Role.ADMIN;
    }
}
