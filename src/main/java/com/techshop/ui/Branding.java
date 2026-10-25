package com.techshop.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Identité visuelle de l'application (design/logo).
 *
 * <p>Le logo source est vectoriel ; les PNG embarqués dans
 * {@code resources/images/brand} sont générés par
 * {@code design/logo/generate/LogoGenerator.java}. Cette classe centralise le
 * chargement du monogramme et des icônes de fenêtre, afin que la marque reste
 * identique dans la barre latérale, les écrans d'authentification et le splash.</p>
 */
public final class Branding {

    private static final String DIR = "/images/brand/";
    /** Monogramme T simple, pour les badges de l'interface (≤ 48 px). */
    private static final String MARK_SMALL = DIR + "techshop-mark-96.png";
    /** Monogramme avec l'indicateur d'accueil, pour les grands affichages. */
    private static final String MARK_LARGE = DIR + "techshop-mark-256.png";
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    /** En dessous, le détail « smartphone » serait une encoche floue. */
    private static final double DETAIL_FROM = 48;

    private Branding() {
    }

    /**
     * Monogramme prêt à poser dans une mise en page. L'image est chargée à
     * {@code 2 × taille} pour rester nette sur les écrans HiDPI.
     *
     * <p>Les petits badges utilisent le master 96 px au T simple : au-delà de
     * 48 px, le master 256 px révèle l'indicateur d'accueil du smartphone.</p>
     */
    public static ImageView mark(double size) {
        ImageView view = new ImageView();
        boolean detail = size > DETAIL_FROM;
        String file = detail ? MARK_LARGE : MARK_SMALL;
        double source = detail ? 256 : 96;
        URL url = Branding.class.getResource(file);
        if (url != null) {
            double requested = Math.min(source, Math.max(size * 2, 48));
            view.setImage(new Image(url.toExternalForm(), requested, requested, true, true));
        }
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    /**
     * Remplace le contenu d'un badge de marque par le logo, sans fond : le
     * monogramme possède déjà sa tuile indigo et ses coins arrondis.
     */
    public static void mount(StackPane badge, double size) {
        badge.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        badge.getChildren().setAll(mark(size));
    }

    /** Icônes de la fenêtre et de la barre des tâches (multi-résolutions). */
    public static void applyIcons(Stage stage) {
        if (stage == null) {
            return;
        }
        for (int size : ICON_SIZES) {
            URL url = Branding.class.getResource(DIR + "app-icon-" + size + ".png");
            if (url != null) {
                stage.getIcons().add(new Image(url.toExternalForm()));
            }
        }
    }
}
