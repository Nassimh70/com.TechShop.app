package com.techshop.model;

/**
 * Définition d'une entrée de navigation : écran et libellés associés.
 * Si {@code fxml} est null, NavigationService affiche l'écran générique
 * « Module en cours de construction » (squelette, SPEC §4).
 */
public final class NavItem {

    private final String id;
    private final String title;
    private final String section;
    private final String icon;       // code Ikonli Feather, ex. "home"
    private final String fxml;       // chemin relatif sous /fxml (null = à venir)
    private final String breadcrumb; // fil d'Ariane (« Accueil »...)

    public NavItem(String id, String title, String section, String icon, String fxml,
                   String breadcrumb) {
        this.id = id;
        this.title = title;
        this.section = section;
        this.icon = icon;
        this.fxml = fxml;
        this.breadcrumb = breadcrumb;
    }

    /** Fabrique : écran accessible à tous les comptes. */
    public static NavItem all(String id, String title, String section, String icon, String fxml) {
        return new NavItem(id, title, section, icon, fxml, null);
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

    public String getBreadcrumb() {
        return breadcrumb;
    }

    @Override
    public String toString() {
        return id;
    }
}
