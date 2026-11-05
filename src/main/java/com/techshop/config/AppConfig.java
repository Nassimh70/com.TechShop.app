package com.techshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration de l'application.
 *
 * <p>La base de données est un fichier SQLite persistant, indépendant du
 * programme d'installation et du dossier de l'application. Son emplacement par
 * défaut est {@code %APPDATA%/TechShopManager/data/techshop.db} (ou
 * {@code ~/.techshop-manager/data/techshop.db} hors Windows). Il peut être
 * surchargé par la propriété système {@code techshop.db.path}, utilisée
 * notamment par les tests, ou par la clé {@code db.path} du fichier externe
 * facultatif {@code config/db.properties}.</p>
 *
 * <p>Le fichier de configuration embarqué ne contient aucun secret. Les
 * réglages de pool restent modifiables sans recompilation.</p>
 */
public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties props = new Properties();

    /** Fichier externe facultatif, à côté du répertoire de travail. */
    private static final Path FICHIER_EXTERNE = Path.of("config", "db.properties");

    static {
        reload();
    }

    private AppConfig() {
    }

    /** (Re)charge la configuration : externe d'abord, ressource embarquée ensuite. */
    public static synchronized void reload() {
        props.clear();
        if (Files.isRegularFile(FICHIER_EXTERNE)) {
            try (InputStream in = Files.newInputStream(FICHIER_EXTERNE)) {
                props.load(in);
                log.info("Configuration chargée depuis {}", FICHIER_EXTERNE.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Lecture de {} impossible, repli sur la config embarquée", FICHIER_EXTERNE, e);
            }
        }
        if (props.isEmpty()) {
            try (InputStream in = AppConfig.class.getResourceAsStream("/config/db.properties")) {
                if (in != null) {
                    props.load(in);
                    log.info("Configuration embarquée /config/db.properties utilisée");
                } else {
                    log.warn("Aucun fichier de configuration trouvé, valeurs par défaut actives");
                }
            } catch (IOException e) {
                log.warn("Configuration embarquée illisible, valeurs par défaut actives", e);
            }
        }
    }

    public static String get(String key, String defaut) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? defaut : v.trim();
    }

    public static int getInt(String key, int defaut) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaut)));
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    // ------------------------------------------------------- emplacements

    /**
     * Répertoire de données persistant de l'application. Jamais le dossier
     * temporaire, les ressources du JAR ni le dossier d'installation.
     */
    public static Path appDataDirectory() {
        String override = System.getProperty("techshop.data.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "TechShopManager").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home", "."), ".techshop-manager")
                .toAbsolutePath().normalize();
    }

    /** Fichier SQLite persistant (jamais créé dans les ressources ou le JAR). */
    public static Path databasePath() {
        String override = System.getProperty("techshop.db.path");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        }
        String configured = get("db.path", "");
        if (!configured.isBlank()) {
            Path p = Path.of(configured.trim());
            return (p.isAbsolute() ? p : appDataDirectory().resolve(p)).toAbsolutePath().normalize();
        }
        return appDataDirectory().resolve("data").resolve("techshop.db");
    }

    /** Dossier des sauvegardes par défaut. */
    public static Path backupDirectory() {
        String override = System.getProperty("techshop.backup.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        }
        String configured = get("backup.dir", "");
        if (!configured.isBlank()) {
            Path p = Path.of(configured.trim());
            return (p.isAbsolute() ? p : appDataDirectory().resolve(p)).toAbsolutePath().normalize();
        }
        return appDataDirectory().resolve("backups");
    }

    /** URL JDBC SQLite (le fichier est créé automatiquement s'il est absent). */
    public static String jdbcUrl() {
        return "jdbc:sqlite:" + databasePath().toString().replace('\\', '/');
    }

    /**
     * Profil d'installation. La production est le défaut ; le profil demo
     * doit être activé explicitement avec {@code -Dtechshop.profile=demo}.
     */
    public static String profile() {
        String profile = System.getProperty("techshop.profile", "prod");
        return "demo".equalsIgnoreCase(profile) ? "demo" : "prod";
    }

    public static boolean isDemoProfile() {
        return "demo".equals(profile());
    }

    // ------------------------------------------------------------- pool

    /** SQLite supporte quelques connexions simultanées en lecture (mode WAL). */
    public static int poolSize() {
        return Math.max(1, getInt("db.pool.size", 4));
    }

    public static int poolMinIdle() {
        return Math.max(0, Math.min(poolSize(), getInt("db.pool.minIdle", 1)));
    }

    public static long poolTimeoutMs() {
        return Math.max(1000, getInt("db.pool.timeoutMs", 30000));
    }
}
