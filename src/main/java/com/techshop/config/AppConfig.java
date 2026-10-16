package com.techshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration de l'application : chargée depuis un fichier externe
 * config/db.properties (prioritaire, modifiable sans recompilation) avec repli
 * sur la configuration embarquée /config/db.properties (SPEC §1).
 * L'écran d'erreur de connexion permet de modifier et d'enregistrer ces valeurs.
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

    /**
     * Enregistre les réglages de connexion dans le fichier externe
     * config/db.properties, puis recharge la configuration en mémoire.
     */
    public static synchronized void saveConnection(String host, int port, String name,
                                                   String user, String password) {
        props.setProperty("db.host", host);
        props.setProperty("db.port", String.valueOf(port));
        props.setProperty("db.name", name);
        props.setProperty("db.user", user);
        props.setProperty("db.password", password == null ? "" : password);
        try {
            Path parent = FICHIER_EXTERNE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(FICHIER_EXTERNE)) {
                props.store(out, "TechShop Manager - configuration de la base (modifiée depuis l'application)");
            }
            log.info("Configuration enregistrée dans {}", FICHIER_EXTERNE.toAbsolutePath());
        } catch (IOException e) {
            log.error("Enregistrement de {} impossible", FICHIER_EXTERNE, e);
        }
        reload();
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

    // --- Accès base de données ---
    public static String dbHost() {
        return get("db.host", "localhost");
    }

    public static int dbPort() {
        return getInt("db.port", 3306);
    }

    public static String dbName() {
        return get("db.name", "techshop");
    }

    public static String dbUser() {
        return get("db.user", "root");
    }

    public static String dbPassword() {
        return get("db.password", "");
    }

    /** URL JDBC sans base précisée (sert à créer la base si absente). */
    public static String jdbcUrlServer() {
        return "jdbc:mariadb://" + dbHost() + ":" + dbPort() + "/";
    }

    /** URL JDBC complète. */
    public static String jdbcUrl() {
        return jdbcUrlServer() + dbName() + "?useUnicode=true&characterEncoding=utf8";
    }

    public static int poolSize() {
        return getInt("db.pool.size", 10);
    }

    public static int poolMinIdle() {
        return getInt("db.pool.minIdle", 2);
    }

    public static long poolTimeoutMs() {
        return getInt("db.pool.timeoutMs", 30000);
    }
}
