package com.techshop.service;

import com.techshop.dao.BrandDao;
import com.techshop.dao.CategoryDao;
import com.techshop.model.ActivityActions;
import com.techshop.model.Brand;
import com.techshop.model.Category;
import com.techshop.util.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Gestion des catégories et des marques (SPEC §4.5).
 * Règles métier : nom obligatoire et unique ; la suppression est logique et
 * refusée si des produits y sont rattachés. Les droits sont vérifiés ici.
 */
public final class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private static final CatalogService INSTANCE =
            new CatalogService(new CategoryDao(), new BrandDao());

    private final CategoryDao categories;
    private final BrandDao brands;

    public CatalogService(CategoryDao categories, BrandDao brands) {
        this.categories = categories;
        this.brands = brands;
    }

    public static CatalogService get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------- catégories

    public List<Category> categories(boolean inclureInactives) {
        try {
            return categories.list(inclureInactives);
        } catch (SQLException e) {
            log.error("Lecture des catégories impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public int createCategory(String name) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.CREATE);
        String nom = validerNom(name, "catégorie");
        try {
            if (categories.nameExists(nom, 0)) {
                throw new ValidationException("Une catégorie porte déjà ce nom.");
            }
            int id = categories.insert(nom, null);
            ActivityLogService.get().log(ActivityActions.CREATION, "categories", id,
                    "Création de la catégorie « " + nom + " »");
            return id;
        } catch (SQLException e) {
            log.error("Création de catégorie impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public void updateCategory(int id, String name, boolean active) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        String nom = validerNom(name, "catégorie");
        try {
            if (categories.nameExists(nom, id)) {
                throw new ValidationException("Une catégorie porte déjà ce nom.");
            }
            categories.update(id, nom, null, active);
            ActivityLogService.get().log(ActivityActions.MODIFICATION, "categories", id,
                    "Modification de la catégorie « " + nom + " »");
        } catch (SQLException e) {
            log.error("Modification de catégorie impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public void setCategoryActive(int id, boolean active) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        try {
            categories.setActive(id, active);
            ActivityLogService.get().log(
                    active ? ActivityActions.ACTIVATION : ActivityActions.DESACTIVATION,
                    "categories", id, (active ? "Activation" : "Désactivation") + " d'une catégorie");
        } catch (SQLException e) {
            log.error("Changement de statut de catégorie impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    // ----------------------------------------------------------------- marques

    public List<Brand> brands(boolean inclureInactives) {
        try {
            return brands.list(inclureInactives);
        } catch (SQLException e) {
            log.error("Lecture des marques impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public int createBrand(String name) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.CREATE);
        String nom = validerNom(name, "marque");
        try {
            if (brands.nameExists(nom, 0)) {
                throw new ValidationException("Une marque porte déjà ce nom.");
            }
            int id = brands.insert(nom);
            ActivityLogService.get().log(ActivityActions.CREATION, "brands", id,
                    "Création de la marque « " + nom + " »");
            return id;
        } catch (SQLException e) {
            log.error("Création de marque impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public void updateBrand(int id, String name, boolean active) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        String nom = validerNom(name, "marque");
        try {
            if (brands.nameExists(nom, id)) {
                throw new ValidationException("Une marque porte déjà ce nom.");
            }
            brands.update(id, nom, active);
            ActivityLogService.get().log(ActivityActions.MODIFICATION, "brands", id,
                    "Modification de la marque « " + nom + " »");
        } catch (SQLException e) {
            log.error("Modification de marque impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    public void setBrandActive(int id, boolean active) {
        PermissionService.get().check(PermissionDefaults.M_PRODUCTS, PermissionService.Action.EDIT);
        try {
            brands.setActive(id, active);
            ActivityLogService.get().log(
                    active ? ActivityActions.ACTIVATION : ActivityActions.DESACTIVATION,
                    "brands", id, (active ? "Activation" : "Désactivation") + " d'une marque");
        } catch (SQLException e) {
            log.error("Changement de statut de marque impossible", e);
            throw new ValidationException("Base de données indisponible. Réessayez plus tard.");
        }
    }

    private static String validerNom(String value, String quoi) {
        String nom = value == null ? "" : value.trim();
        if (nom.length() < 2) {
            throw new ValidationException("Le nom de la " + quoi + " doit comporter au moins 2 caractères.");
        }
        if (nom.length() > 80) {
            throw new ValidationException("Le nom de la " + quoi + " est trop long (80 caractères maximum).");
        }
        return nom;
    }
}
