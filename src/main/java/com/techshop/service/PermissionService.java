package com.techshop.service;

import com.techshop.dao.PermissionDao;
import com.techshop.model.NavItem;
import com.techshop.model.Permission;
import com.techshop.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vérification des permissions (SPEC §3) : « les permissions sont vérifiées
 * dans les services, pas seulement masquées dans l'interface ».
 * La matrice vient de la table « permissions » ; si la base n'est pas joignable,
 * on retombe sur {@link PermissionDefaults} (miroir du jeu de données initial).
 * Le filtrage du menu ({@link #visibleItems(Role)}) s'appuie sur la même source.
 */
public final class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** Actions contrôlées (colonnes de la table permissions). */
    public enum Action {
        VIEW, CREATE, EDIT, DELETE, VALIDATE
    }

    private static final PermissionService INSTANCE = new PermissionService(new PermissionDao());

    private final PermissionDao dao;

    /** Matrice chargée depuis la base, ou null tant qu'elle n'a pas été lue. */
    private volatile Map<Role, Map<String, Permission>> matriceBase;

    /** Repli hors base (toujours disponible). */
    private final Map<Role, Map<String, Permission>> matriceDefaut = PermissionDefaults.matrice();

    public PermissionService(PermissionDao dao) {
        this.dao = dao;
    }

    public static PermissionService get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------ chargement

    /** (Re)charge la matrice depuis la base ; conserve le repli en cas d'échec. */
    public void reload() {
        try {
            matriceBase = dao.loadAll();
            log.info("Permissions chargées depuis la base ({} rôle(s))", matriceBase.size());
        } catch (SQLException e) {
            matriceBase = null;
            log.warn("Permissions indisponibles en base, repli sur les valeurs par défaut : {}",
                    e.getMessage());
        }
    }

    // ----------------------------------------------------------- consultation

    /** A-t-on le droit demandé sur le module, pour le rôle effectif de la session ? */
    public boolean can(String module, Action action) {
        return can(SessionContext.get().getRole(), module, action);
    }

    /** A-t-on le droit demandé sur le module, pour un rôle explicite ? */
    public boolean can(Role role, String module, Action action) {
        if (module == null || !modulesConnus().contains(module)) {
            // Module hors matrice : aucune restriction ici.
            return true;
        }
        Permission p = permission(role, module);
        if (p == null) {
            // Module connu mais aucune ligne pour ce rôle : aucun droit.
            return false;
        }
        return switch (action) {
            case VIEW -> p.canView();
            case CREATE -> p.canCreate();
            case EDIT -> p.canEdit();
            case DELETE -> p.canDelete();
            case VALIDATE -> p.canValidate();
        };
    }

    public boolean canView(Role role, String module) {
        return can(role, module, Action.VIEW);
    }

    /** Permission d'un rôle sur un module (repli par défaut si module inconnu). */
    public Permission permission(Role role, String module) {
        Map<String, Permission> parRole = matrice(role);
        return parRole.get(module);
    }

    /** Matrice d'un rôle (base si disponible, sinon repli). */
    public Map<String, Permission> matrixFor(Role role) {
        return matrice(role);
    }

    private Map<Role, Map<String, Permission>> matriceActive() {
        Map<Role, Map<String, Permission>> base = matriceBase;
        return base == null ? matriceDefaut : base;
    }

    private Map<String, Permission> matrice(Role role) {
        Role r = role == null ? Role.ADMIN : role;
        return matriceActive().getOrDefault(r, new LinkedHashMap<>());
    }

    // ------------------------------------------------------------- contrôles

    /**
     * Contrôle d'accès applicatif : à appeler en tête des services sensibles.
     *
     * @throws SecurityException si le rôle n'a pas la permission
     */
    public void check(String module, Action action) {
        if (!can(module, action)) {
            throw new SecurityException("Accès refusé : rôle "
                    + SessionContext.get().getRole().getLabel() + " — action " + action
                    + " sur le module « " + module + " »");
        }
    }

    // ---------------------------------------------------------------- menus

    /** Accès à un module/écran pour le rôle effectif de la session. */
    public boolean canAccess(NavItem item) {
        return canAccess(item, SessionContext.get().getRole());
    }

    /**
     * Accès à un module/écran pour un rôle explicite. Le rôle doit à la fois
     * figurer dans la définition de l'écran (garde structurelle) et disposer du
     * droit « consulter » sur le module correspondant.
     */
    public boolean canAccess(NavItem item, Role role) {
        if (item == null || role == null || !item.allows(role)) {
            return false;
        }
        // Module hors matrice : la garde structurelle suffit.
        if (!modulesConnus().contains(item.getId())) {
            return true;
        }
        return canView(role, item.getId());
    }

    /** Écrans visibles pour un rôle (filtrage du menu latéral). */
    public List<NavItem> visibleItems(Role role) {
        List<NavItem> visibles = new ArrayList<>();
        for (NavItem item : MenuRegistry.all()) {
            if (canAccess(item, role)) {
                visibles.add(item);
            }
        }
        return visibles;
    }

    /**
     * Contrôle d'accès applicatif sur un écran.
     *
     * @throws SecurityException si le rôle n'a pas la permission
     */
    public void check(NavItem item) {
        if (!canAccess(item)) {
            throw new SecurityException("Accès refusé : rôle "
                    + SessionContext.get().getRole().getLabel() + " non autorisé pour « "
                    + (item == null ? "?" : item.getTitle()) + " »");
        }
    }

    // ------------------------------------------------------------- utilitaires

    /** Modules de la matrice (base si disponible, sinon liste par défaut). */
    public List<String> modules() {
        return new ArrayList<>(modulesConnus());
    }

    /** Ensemble des modules référencés par au moins un rôle (union). */
    private java.util.Set<String> modulesConnus() {
        java.util.Set<String> modules = new java.util.LinkedHashSet<>();
        for (Map<String, Permission> parRole : matriceActive().values()) {
            modules.addAll(parRole.keySet());
        }
        return modules;
    }
}
