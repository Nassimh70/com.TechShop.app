package com.techshop.service;

import com.techshop.model.Role;
import com.techshop.model.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Session courante : utilisateur connecté + rôle effectif.
 * (Un aperçu de rôle permet de visualiser l'interface VENDEUR/TECHNICIEN
 *  depuis le menu utilisateur — utile tant que l'écran de connexion n'existe pas.)
 */
public final class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();

    private final ObjectProperty<User> user = new SimpleObjectProperty<>();
    private final ObjectProperty<Role> displayRole = new SimpleObjectProperty<>();

    private SessionContext() {
    }

    public static SessionContext get() {
        return INSTANCE;
    }

    // --- Utilisateur ---
    public void setUser(User u) {
        user.set(u);
        displayRole.set(u == null ? null : u.getRole());
    }

    /** Déconnexion : vide l'utilisateur et le rôle d'aperçu. */
    public void logout() {
        user.set(null);
        displayRole.set(null);
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

    // --- Rôle effectif ---
    public Role getRole() {
        Role r = displayRole.get();
        if (r != null) {
            return r;
        }
        User u = user.get();
        return u == null ? Role.ADMIN : u.getRole();
    }

    public void setDisplayRole(Role role) {
        displayRole.set(role);
    }

    public ObjectProperty<Role> displayRoleProperty() {
        return displayRole;
    }

    /** L'utilisateur réel (différent du rôle d'aperçu le cas échéant). */
    public Role realRole() {
        User u = user.get();
        return u == null ? Role.ADMIN : u.getRole();
    }
}
