package com.techshop.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * Point d'accès unique à la base MySQL/MariaDB (XAMPP) via HikariCP.
 * Crée la base « techshop » si absente, démarre le pool puis applique les
 * migrations versionnées ({@link MigrationService}). Toute défaillance est
 * journalisée et exposée à l'écran d'erreur de connexion (SPEC §1, §5, §7).
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Object LOCK = new Object();

    private static HikariDataSource pool;
    private static volatile boolean initialized = false;
    private static volatile String lastError;
    private static volatile List<MigrationService.Applied> lastMigrations = List.of();

    /**
     * Connexion de la transaction en cours pour le thread courant.
     * Permet aux DAO (qui ouvrent chacun une connexion) de partager la même
     * transaction sans changer leur code : la connexion renvoyée par
     * {@link #connection()} ne se ferme pas (voir {@link #nonClosable}).
     */
    private static final ThreadLocal<Connection> TRANSACTION = new ThreadLocal<>();

    private DatabaseManager() {
    }

    /** Une transaction JDBC est-elle en cours sur ce thread ? */
    public static boolean inTransaction() {
        return TRANSACTION.get() != null;
    }

    /**
     * Démarre une transaction sur le thread courant. Sans pool actif
     * (tests unitaires), l'appel est neutre : {@link #inTransaction()} reste faux.
     */
    public static void beginTransaction() throws SQLException {
        if (pool == null || !initialized) {
            return;
        }
        Connection c = pool.getConnection();
        c.setAutoCommit(false);
        TRANSACTION.set(c);
    }

    /** Valide la transaction en cours (sans effet s'il n'y en a pas). */
    public static void commitTransaction() {
        Connection c = TRANSACTION.get();
        if (c != null) {
            try {
                c.commit();
            } catch (SQLException e) {
                log.warn("Validation de transaction impossible : {}", e.getMessage());
            }
        }
    }

    /** Annule la transaction en cours (sans effet s'il n'y en a pas). */
    public static void rollbackTransaction() {
        Connection c = TRANSACTION.get();
        if (c != null) {
            try {
                c.rollback();
            } catch (SQLException e) {
                log.warn("Annulation de transaction impossible : {}", e.getMessage());
            }
        }
    }

    /** Termine la transaction en cours : libère la connexion vers le pool. */
    public static void endTransaction() {
        Connection c = TRANSACTION.get();
        TRANSACTION.remove();
        if (c != null) {
            try {
                c.setAutoCommit(true);
            } catch (SQLException ignored) {
                // rien
            }
            try {
                c.close();
            } catch (SQLException ignored) {
                // rien
            }
        }
    }

    /**
     * Connexion partagée de la transaction en cours (fermeture neutralisée),
     * ou nouvelle connexion du pool. À fermer par l'appelant.
     */
    private static Connection transactionConnection() {
        Connection c = TRANSACTION.get();
        if (c == null) {
            return null;
        }
        return nonClosable(c);
    }

    /** Enveloppe une connexion pour que {@code close()} soit sans effet. */
    private static Connection nonClosable(Connection reelle) {
        return (Connection) Proxy.newProxyInstance(
                DatabaseManager.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, methode, args) -> {
                    if ("close".equals(methode.getName())) {
                        return null;
                    }
                    try {
                        return methode.invoke(reelle, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** Le pool est-il prêt (base joignable et schéma à jour) ? */
    public static boolean isReady() {
        return initialized;
    }

    /** Dernier message d'erreur lisible (null si la base est prête). */
    public static String getLastError() {
        return lastError;
    }

    /** Dernières migrations appliquées lors de l'initialisation. */
    public static List<MigrationService.Applied> getLastMigrations() {
        return lastMigrations;
    }

    /** Connexion du pool (ou connexion partagée si une transaction est en cours). */
    public static Connection connection() throws SQLException {
        Connection partagee = transactionConnection();
        if (partagee != null) {
            return partagee;
        }
        if (pool == null) {
            throw new SQLException("Base de données non initialisée");
        }
        return pool.getConnection();
    }

    /**
     * Initialisation complète : création de la base si absente, pool HikariCP,
     * application des migrations. En cas d'échec, {@link #getLastError()} est
     * renseigné et l'application reste utilisable (mode dégradé).
     */
    public static void init() {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                createDatabaseIfMissing();
                createPool();
                lastMigrations = MigrationService.apply(pool);
                initialized = true;
                lastError = null;
                log.info("Base « {} » prête ({} migration(s) appliquée(s))",
                        AppConfig.dbName(), lastMigrations.size());
            } catch (Exception e) {
                lastError = messageUtilisateur(e);
                log.error("Base de données indisponible : {}", lastError, e);
                closeQuietly();
            }
        }
    }

    /** Réessaie après un échec (bouton « Réessayer » / réglages modifiés). */
    public static void reinit() {
        shutdown();
        init();
    }

    /** Ferme proprement le pool (arrêt de l'application). */
    public static void shutdown() {
        synchronized (LOCK) {
            closeQuietly();
            initialized = false;
        }
    }

    /**
     * Teste une configuration de connexion sans démarrer le pool applicatif.
     *
     * @return null si la connexion réussit, sinon le message d'erreur
     */
    public static String testConnection(String host, int port, String user, String password) {
        String url = "jdbc:mariadb://" + host + ":" + port + "/?connectTimeout=4000";
        Properties p = new Properties();
        p.setProperty("user", user == null ? "" : user);
        p.setProperty("password", password == null ? "" : password);
        try (Connection c = DriverManager.getConnection(url, p)) {
            return c.isValid(3) ? null : "Connexion établie mais invalide";
        } catch (SQLException e) {
            return messageUtilisateur(e);
        }
    }

    // ------------------------------------------------------------------ privés

    private static void createDatabaseIfMissing() throws SQLException {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.jdbcUrlServer() + "?connectTimeout=4000");
        cfg.setUsername(AppConfig.dbUser());
        cfg.setPassword(AppConfig.dbPassword());
        cfg.setMaximumPoolSize(1);
        cfg.setConnectionTimeout(4000);
        try (HikariDataSource ds = new HikariDataSource(cfg);
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            // Compatible MySQL et MariaDB (pas de fonction exclusive MySQL 8)
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + AppConfig.dbName()
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void createPool() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.jdbcUrl());
        cfg.setUsername(AppConfig.dbUser());
        cfg.setPassword(AppConfig.dbPassword());
        cfg.setMaximumPoolSize(AppConfig.poolSize());
        cfg.setMinimumIdle(AppConfig.poolMinIdle());
        cfg.setConnectionTimeout(AppConfig.poolTimeoutMs());
        cfg.setPoolName("techshop-pool");
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        pool = new HikariDataSource(cfg);
    }

    /** Message clair pour l'utilisateur (écran d'erreur de connexion). */
    private static String messageUtilisateur(Exception e) {
        String brut = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String bas = brut.toLowerCase();
        if (bas.contains("connection refused") || bas.contains("connectex") || bas.contains("getsockopt")) {
            return "Impossible de joindre MySQL sur " + AppConfig.dbHost() + ":"
                    + AppConfig.dbPort() + ". Vérifiez que le module MySQL (ou MariaDB) de XAMPP est démarré.";
        }
        if (bas.contains("access denied") || bas.contains("authentication")) {
            return "Accès refusé par le serveur pour l'utilisateur « " + AppConfig.dbUser()
                    + " ». Vérifiez l'identifiant et le mot de passe dans les réglages de connexion.";
        }
        if (bas.contains("unknown database")) {
            return "La base « " + AppConfig.dbName() + " » est introuvable et n'a pas pu être créée.";
        }
        if (bas.contains("timeout") || bas.contains("timed out")) {
            return "Délai de connexion dépassé vers " + AppConfig.dbHost() + ":"
                    + AppConfig.dbPort() + ". Vérifiez le serveur et le pare-feu.";
        }
        return brut;
    }

    private static void closeQuietly() {
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ignored) {
                // rien
            }
            pool = null;
        }
    }
}
