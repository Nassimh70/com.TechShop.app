package com.techshop.util;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Chargement de la police Inter embarquée dans resources/fonts (SPEC §2).
 * Repli automatique sur Segoe UI si les fichiers sont absents.
 */
public final class Fonts {

    private static final Logger log = LoggerFactory.getLogger(Fonts.class);

    private static final List<String> FICHIERS = List.of(
            "Inter_400Regular.ttf", "Inter_500Medium.ttf",
            "Inter_600SemiBold.ttf", "Inter_700Bold.ttf");

    public static final String FALLBACK = "Segoe UI";

    private Fonts() {
    }

    /**
     * Charge les variantes Inter et retourne le nom de famille à utiliser
     * dans les styles (Inter si disponible, sinon Segoe UI).
     */
    public static String install() {
        boolean charge = false;
        for (String f : FICHIERS) {
            try (InputStream in = Fonts.class.getResourceAsStream("/fonts/" + f)) {
                if (in == null) {
                    continue;
                }
                Font font = Font.loadFont(in, 13);
                if (font != null) {
                    charge = true;
                }
            } catch (IOException e) {
                log.warn("Police {} illisible : {}", f, e.getMessage());
            }
        }
        if (charge) {
            log.info("Police Inter chargée");
            return "Inter";
        }
        log.warn("Police Inter absente : repli sur {}", FALLBACK);
        return FALLBACK;
    }
}
