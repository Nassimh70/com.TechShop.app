package com.techshop.model;

/** Marque de produits (SPEC §5). Suppression logique via la colonne active. */
public record Brand(int id, String name, boolean active) {

    @Override
    public String toString() {
        return name;
    }
}