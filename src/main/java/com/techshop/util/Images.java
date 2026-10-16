package com.techshop.util;

import javafx.scene.image.Image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Images de produits (SPEC §4.5) : copie du fichier choisi dans un dossier de
 * l'application (data/products) et chargement des miniatures.
 * Le chemin enregistré en base est relatif, pour rester portable.
 */
public final class Images {

    private static final Path DOSSIER = Path.of(System.getProperty("user.dir"), "data", "products");

    private Images() {
    }

    /** Dossier des images de produits (créé si besoin). */
    public static Path dossier() throws IOException {
        Files.createDirectories(DOSSIER);
        return DOSSIER;
    }

    /**
     * Copie une image dans le dossier de l'application.
     *
     * @return le chemin relatif à enregistrer en base (data/products/...)
     */
    public static String copyIn(Path source, String prefix) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Fichier image introuvable.");
        }
        dossier();
        String extension = extension(source.getFileName().toString());
        String base = (prefix == null || prefix.isBlank() ? "produit" : prefix)
                .replaceAll("[^A-Za-z0-9_-]", "_");
        String nom = base + "-" + System.currentTimeMillis() + extension;
        Path cible = DOSSIER.resolve(nom);
        Files.copy(source, cible, StandardCopyOption.REPLACE_EXISTING);
        return "data/products/" + nom;
    }

    /** Chemin absolu d'une image enregistrée en base (ou null). */
    public static Path resolve(String cheminRelatif) {
        if (cheminRelatif == null || cheminRelatif.isBlank()) {
            return null;
        }
        Path brut = Path.of(cheminRelatif);
        return brut.isAbsolute() ? brut : Path.of(System.getProperty("user.dir")).resolve(brut);
    }

    /** Miniature pour le tableau / la grille, ou null si absente. */
    public static Image thumbnail(String cheminRelatif, double taille) {
        Path fichier = resolve(cheminRelatif);
        if (fichier == null || !Files.isRegularFile(fichier)) {
            return null;
        }
        Image image = new Image(fichier.toUri().toString(), taille, taille, true, true, false);
        return image.isError() ? null : image;
    }

    private static String extension(String nom) {
        int point = nom.lastIndexOf('.');
        if (point < 0) {
            return ".png";
        }
        String ext = nom.substring(point).toLowerCase(Locale.ROOT);
        return ext.matches("\\.(png|jpg|jpeg|gif|bmp|webp)") ? ext : ".png";
    }
}
