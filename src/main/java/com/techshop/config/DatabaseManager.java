package com.techshop.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Point d'accès unique à la base SQLite via HikariCP.
 *
 * <p>Au premier démarrage, le dossier de données et le fichier
 * {@code techshop.db} sont créés automatiquement. Le fichier existant n'est
 * jamais réinitialisé. Les migrations versionnées sont appliquées ensuite par
 * {@link MigrationService}. Les clés étrangères sont activées sur chaque
 * connexion du pool.</p>
 *
 * <p>Toute défaillance est journalisée et exposée à l'écran d'erreur de
 * connexion. L'application reste utilisable en mode dégradé.</p>
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
        if (pool == null || !initialized || TRANSACTION.get() != null) {
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
     * Initialisation complète : création du dossier et du fichier SQLite si
     * absents, configuration WAL, pool HikariCP, application des migrations.
     * En cas d'échec, {@link #getLastError()} est renseigné et l'application
     * reste utilisable (mode dégradé).
     */
    public static void init() {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                Path fichier = AppConfig.databasePath();
                preparerDossier(fichier);
                configurerFichier(fichier);
                createPool();
                lastMigrations = MigrationService.apply(pool);
                initialized = true;
                lastError = null;
                log.info("Base SQLite « {} » prête ({} migration(s) appliquée(s))",
                        fichier, lastMigrations.size());
            } catch (Exception e) {
                lastError = messageUtilisateur(e);
                log.error("Base de données indisponible : {}", lastError, e);
                closeQuietly();
                initialized = false;
            }
        }
    }

    /** Réessaie après un échec (bouton « Réessayer » / dossier modifié). */
    public static void reinit() {
        shutdown();
        init();
    }

    /** Ferme proprement le pool (arrêt de l'application). */
    public static void shutdown() {
        synchronized (LOCK) {
            endTransaction();
            closeQuietly();
            initialized = false;
        }
    }

    /**
     * Teste l'ouverture du fichier SQLite sans démarrer le pool applicatif.
     *
     * @return null si la connexion réussit, sinon le message d'erreur
     */
    public static String testConnection() {
        try {
            Path fichier = AppConfig.databasePath();
            preparerDossier(fichier);
            try (Connection c = DriverManager.getConnection(AppConfig.jdbcUrl());
                 Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                return c.isValid(3) ? null : "Connexion établie mais invalide";
            }
        } catch (Exception e) {
            return messageUtilisateur(e);
        }
    }

    // ------------------------------------------------------------------ privés

    /** Crée le dossier de données et vérifie que le chemin est utilisable. */
    private static void preparerDossier(Path fichier) throws SQLException {
        Path dossier = fichier.getParent();
        if (dossier != null) {
            try {
                Files.createDirectories(dossier);
            } catch (IOException e) {
                throw new SQLException("Impossible de créer le dossier de données « "
                        + dossier.toAbsolutePath() + " ».", e);
            }
            if (!Files.isDirectory(dossier) || !Files.isWritable(dossier)) {
                throw new SQLException("Le dossier de données « " + dossier.toAbsolutePath()
                        + " » n'est pas accessible en écriture.");
            }
        }
        if (Files.exists(fichier) && !Files.isRegularFile(fichier)) {
            throw new SQLException("Le chemin de la base « " + fichier.toAbsolutePath()
                    + " » existe mais n'est pas un fichier.");
        }
    }

    /**
     * Ouvre une première connexion directe pour créer le fichier si besoin et
     * fixer les réglages persistants (WAL, synchronisation, délai d'attente).
     */
    private static void configurerFichier(Path fichier) throws SQLException {
        try (Connection c = DriverManager.getConnection(AppConfig.jdbcUrl());
             Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA foreign_keys = ON");
        }
        if (!Files.isRegularFile(fichier)) {
            throw new SQLException("Le fichier SQLite n'a pas pu être créé : "
                    + fichier.toAbsolutePath());
        }
    }

    private static void createPool() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.jdbcUrl());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(AppConfig.poolSize());
        cfg.setMinimumIdle(AppConfig.poolMinIdle());
        cfg.setConnectionTimeout(AppConfig.poolTimeoutMs());
        cfg.setPoolName("techshop-sqlite-pool");
        cfg.setAutoCommit(true);
        // Les clés étrangères sont propres à chaque connexion SQLite.
        cfg.setConnectionInitSql("PRAGMA foreign_keys = ON");
        cfg.addDataSourceProperty("foreign_keys", "true");
        cfg.addDataSourceProperty("busy_timeout", "5000");
        cfg.addDataSourceProperty("date_class", "TEXT");
        cfg.addDataSourceProperty("date_string_format", "yyyy-MM-dd HH:mm:ss");
        pool = new HikariDataSource(cfg);
    }

    /** Message clair pour l'utilisateur (écran d'erreur de connexion). */
    private static String messageUtilisateur(Throwable e) {
        Path fichier = AppConfig.databasePath();
        int code = sqliteCode(e);
        String bas = messageBrut(e).toLowerCase();

        if (code == 5 || code == 6 || bas.contains("sqlite_busy")
                || bas.contains("database is locked")) {
            return "La base de données est momentanément verrouillée. Fermez les autres "
                    + "instances de TechShop Manager puis réessayez.";
        }
        if (code == 8 || bas.contains("readonly") || bas.contains("read-only")) {
            return "Le fichier de base de données est en lecture seule : " + fichier
                    + ". Vérifiez les droits du dossier de données.";
        }
        if (code == 11 || code == 26 || bas.contains("malformed")
                || bas.contains("not a database")) {
            return "Le fichier de base de données est corrompu ou n'est pas une base SQLite "
                    + "valide : " + fichier + ". Restaurez une sauvegarde récente.";
        }
        if (code == 13 || bas.contains("disk is full") || bas.contains("disk i/o error")) {
            return "Espace disque insuffisant ou erreur d'écriture pour la base : " + fichier + ".";
        }
        if (code == 14 || bas.contains("unable to open database")
                || bas.contains("cannot open")) {
            return "Impossible d'ouvrir le fichier de base de données : " + fichier
                    + ". Vérifiez que le dossier existe et est accessible en écriture.";
        }
        if (bas.contains("no such table") || bas.contains("no such column")) {
            return "Le schéma de la base est incomplet : " + fichier
                    + ". Redémarrez l'application pour appliquer les migrations.";
        }
        if (bas.contains("foreign key constraint failed")) {
            return "Une contrainte d'intégrité a été violée. Aucune modification n'a été "
                    + "conservée. Vérifiez les données saisies.";
        }
        return messageBrut(e);
    }

    private static int sqliteCode(Throwable e) {
        Throwable courant = e;
        while (courant != null) {
            if (courant instanceof SQLException sql && sql.getErrorCode() != 0) {
                return sql.getErrorCode();
            }
            courant = courant.getCause();
        }
        return 0;
    }

    private static String messageBrut(Throwable e) {
        Throwable courant = e;
        while (courant != null) {
            if (courant.getMessage() != null && !courant.getMessage().isBlank()) {
                return courant.getMessage();
            }
            courant = courant.getCause();
        }
        return e.getClass().getSimpleName();
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
