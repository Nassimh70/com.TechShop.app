package com.techshop.model;

/**
 * Catégorie de produits (SPEC §5). Suppression logique via « active ».
 * Le parent permet une hiérarchie facultative.
 */
public record Category(int id, String name, Integer parentId, String parentName, boolean active) {

    public Category(int id, String name, boolean active) {
        this(id, name, null, null, active);
    }

    @Override
    public String toString() {
        return name;
    }
}