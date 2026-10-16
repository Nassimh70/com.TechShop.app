package com.techshop.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Définition d'une entrée de navigation : écran + rôles autorisés.
 * Si {@code fxml} est null, NavigationService affiche l'écran générique
 * « Module en cours de construction » (squelette, SPEC §4).
 */
public final class NavItem {

    private final String id;
    private final String title;
    private final String section;
    private final String icon;       // code Ikonli Feather, ex. "home"
    private final String fxml;       // chemin relatif sous /fxml (null = à venir)
    private final Set<Role> roles;
    private final String breadcrumb; // fil d'Ariane (« Accueil »...)

    public NavItem(String id, String title, String section, String icon, String fxml,
                   String breadcrumb, Set<Role> roles) {
        this.id = id;
        this.title = title;
        this.section = section;
        this.icon = icon;
        this.fxml = fxml;
        this.breadcrumb = breadcrumb;
        this.roles = roles == null || roles.isEmpty() ? EnumSet.allOf(Role.class) : roles;
    }

    /** Fabrique : écran accessible à tous les rôles. */
    public static NavItem all(String id, String title, String section, String icon, String fxml) {
        return new NavItem(id, title, section, icon, fxml, null, EnumSet.allOf(Role.class));
    }

    /** Fabrique : écran restreint à certains rôles. */
    public static NavItem of(String id, String title, String section, String icon, String fxml,
                             Role... roles) {
        return new NavItem(id, title, section, icon, fxml, null, EnumSet.of(roles[0], roles));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSection() {
        return section;
    }

    public String getIcon() {
        return icon;
    }

    public String getFxml() {
        return fxml;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public String getBreadcrumb() {
        return breadcrumb;
    }

    public boolean allows(Role role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return id;
    }
}
