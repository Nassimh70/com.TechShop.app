package com.techshop.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Chemins persistants de l'installation, indépendants du dossier du programme. */
public final class AppPaths {

    private static final String APP_NAME = "TechShop Manager";

    private AppPaths() {
    }

    public static Path dataDirectory() {
        return appDataDirectory().resolve("data");
    }

    public static Path configDirectory() {
        return appDataDirectory().resolve("config");
    }

    public static Path logsDirectory() {
        return appDataDirectory().resolve("logs");
    }

    public static Path portableMariaDbDirectory() {
        return appDataDirectory().resolve("runtime").resolve("mariadb");
    }

    public static Path appDataDirectory() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        Path directory = base.resolve(APP_NAME);
        try {
            Files.createDirectories(directory);
        } catch (Exception ignored) {
            // Les appelants signalent les erreurs lors de la création effective.
        }
        return directory;
    }
}
