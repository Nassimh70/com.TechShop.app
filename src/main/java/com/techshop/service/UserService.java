package com.techshop.service;

import com.techshop.dao.PermissionDao;
import com.techshop.dao.UserDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.Permission;
import com.techshop.model.Role;
import com.techshop.model.User;
import com.techshop.util.Passwords;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Gestion des utilisateurs et des permissions (SPEC §3, §4.12, §6).
 * Toutes les règles métier (unicité de l'identifiant, protection du dernier
 * administrateur, robustesse du mot de passe, droits d'accès) sont vérifiées
 * ici, dans la couche service, et pas seulement masquées dans l'interface.
 */
public final class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Identifiant de connexion : lettres, chiffres, point, tiret, souligné. */
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,60}");

    private static final UserService INSTANCE = new UserService(
            new UserDao(), new PermissionDao(), ActivityLogService.get(), PermissionService.get());

    private final UserDao users;
    private final PermissionDao permissions;
    private final ActivityLogService journal;
    private final PermissionService permissionService;

    public UserService(UserDao users, PermissionDao permissions,
                       ActivityLogService journal, PermissionService permissionService) {
        this.users = users;
        this.permissions = permissions;
        this.journal = journal;
        this.permissionService = permissionService;
    }

    public static UserService get() {
        return INSTANCE;
    }

    // -------------------------------------------------------------- lecture

    /** Liste filtrée des utilisateurs (module Utilisateurs). */
    public List<User> list(String recherche, Role role, Boolean actif) {
        try {
            return users.search(recherche, role, actif);
        } catch (SQLException e) {
            log.error("Lecture des utilisateurs impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public User byId(int id) {
        try {
            return users.findById(id).orElseThrow(
                    () -> new ValidationException("Utilisateur introuvable."));
        } catch (SQLException e) {
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ------------------------------------------------------------ écriture

    /** Crée un utilisateur après validation ; le mot de passe est haché (BCrypt). */
    public int create(String username, String fullName, String phone, String email,
                      Role role, String motDePasse, boolean forcerChangement) {
        exigerPermission(Action.CREATE);
        String id = validerIdentifiant(username, 0);
        String nom = validerNom(fullName);
        if (role == null) {
            throw new ValidationException("role", "Veuillez choisir un rôle.");
        }
        String erreur = Passwords.validate(motDePasse);
        if (erreur != null) {
            throw new ValidationException("motDePasse", erreur);
        }
        try {
            String hash = Passwords.hash(motDePasse);
            int newId = users.insert(id, hash, nom, phone, email, role, true, forcerChangement);
            journal.log(ActivityActions.CREATION, "users", newId,
                    "Création de l'utilisateur « " + id + " » (" + role.getLabel() + ")");
            return newId;
        } catch (SQLException e) {
            log.error("Création d'utilisateur impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Met à jour les informations d'un utilisateur (hors mot de passe). */
    public void update(int id, String fullName, String phone, String email,
                       Role role, boolean active) {
        exigerPermission(Action.EDIT);
        String nom = validerNom(fullName);
        if (role == null) {
            throw new ValidationException("role", "Veuillez choisir un rôle.");
        }
        try {
            User existant = users.findById(id).orElseThrow(
                    () -> new ValidationException("Utilisateur introuvable."));
            // Protection : ne pas retirer le rôle ADMIN du dernier administrateur actif
            if (existant.getRole() == Role.ADMIN && role != Role.ADMIN && existant.isActive()
                    && active && users.countActiveAdmins() <= 1) {
                throw new ValidationException("role",
                        "Impossible de retirer le rôle du dernier administrateur actif.");
            }
            users.update(id, nom, phone, email, role, active);
            journal.log(ActivityActions.MODIFICATION, "users", id,
                    "Modification de « " + existant.getUsername() + " »");
        } catch (SQLException e) {
            log.error("Modification d'utilisateur impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Active ou désactive un compte, en protégeant le compte courant et le dernier admin. */
    public void setActive(int id, boolean active) {
        exigerPermission(Action.EDIT);
        try {
            User cible = users.findById(id).orElseThrow(
                    () -> new ValidationException("Utilisateur introuvable."));
            User courant = SessionContext.get().getUser();
            if (!active && courant != null && courant.getId() == id) {
                throw new ValidationException(
                        "Vous ne pouvez pas désactiver votre propre compte.");
            }
            if (!active && cible.getRole() == Role.ADMIN && cible.isActive()
                    && users.countActiveAdmins() <= 1) {
                throw new ValidationException(
                        "Impossible de désactiver le dernier administrateur actif.");
            }
            users.setActive(id, active);
            journal.log(active ? ActivityActions.ACTIVATION : ActivityActions.DESACTIVATION,
                    "users", id, (active ? "Activation" : "Désactivation") + " de « "
                            + cible.getUsername() + " »");
        } catch (SQLException e) {
            log.error("Changement de statut impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /**
     * Réinitialise le mot de passe d'un utilisateur : un mot de passe temporaire
     * est généré et le changement est rendu obligatoire à la prochaine connexion.
     *
     * @return le mot de passe temporaire en clair (à communiquer une seule fois)
     */
    public String resetPassword(int id) {
        exigerPermission(Action.EDIT);
        try {
            User cible = users.findById(id).orElseThrow(
                    () -> new ValidationException("Utilisateur introuvable."));
            String temporaire = Passwords.temporary();
            users.updatePassword(id, Passwords.hash(temporaire), true);
            journal.log(ActivityActions.REINIT_MDP, "users", id,
                    "Réinitialisation du mot de passe de « " + cible.getUsername() + " »");
            return temporaire;
        } catch (SQLException e) {
            log.error("Réinitialisation de mot de passe impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ------------------------------------------------------------ permissions

    /** Matrice de permissions d'un rôle. */
    public Map<String, Permission> permissionsOf(Role role) {
        return permissionService.matrixFor(role);
    }

    /** Enregistre la matrice de permissions d'un rôle (réservé aux administrateurs). */
    public void savePermissions(Role role, List<Permission> lignes) {
        exigerPermission(Action.EDIT);
        if (role == null) {
            throw new ValidationException("Veuillez choisir un rôle.");
        }
        try {
            permissions.replaceRole(role, lignes);
            permissionService.reload();
            journal.log(ActivityActions.PERMISSIONS, "permissions", null,
                    "Mise à jour des permissions du rôle " + role.getLabel());
        } catch (SQLException e) {
            log.error("Enregistrement des permissions impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    /** Vérifie qu'une permission est bien accordée au rôle de la session. */
    public void verifier(Role role, String module, PermissionService.Action action) {
        if (!permissionService.can(role, module, action)) {
            throw new SecurityException("Accès refusé : rôle " + role.getLabel()
                    + " — action " + action + " sur « " + module + " »");
        }
    }

    // --------------------------------------------------------------- validation

    private void exigerPermission(Action action) {
        PermissionService.Action a = action == Action.CREATE
                ? PermissionService.Action.CREATE : PermissionService.Action.EDIT;
        if (!permissionService.can(PermissionDefaults.M_USERS, a)) {
            throw new SecurityException("Accès refusé : gestion des utilisateurs non autorisée.");
        }
    }

    private enum Action {
        CREATE, EDIT
    }

    private String validerIdentifiant(String username, int excludeId) {
        String id = username == null ? "" : username.trim();
        if (!USERNAME.matcher(id).matches()) {
            throw new ValidationException("username",
                    "L'identifiant doit comporter 3 à 60 caractères (lettres, chiffres, . _ -).");
        }
        try {
            if (users.usernameExists(id, excludeId)) {
                throw new ValidationException("username",
                        "Cet identifiant est déjà utilisé.");
            }
        } catch (SQLException e) {
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
        return id;
    }

    private String validerNom(String fullName) {
        String nom = fullName == null ? "" : fullName.trim();
        if (nom.length() < 3 || nom.length() > 120) {
            throw new ValidationException("fullName",
                    "Le nom complet doit comporter 3 à 120 caractères.");
        }
        return nom;
    }
}
