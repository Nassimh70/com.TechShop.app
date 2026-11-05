package com.techshop.service;

import com.techshop.config.AppConfig;
import com.techshop.config.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sauvegarde et restauration du fichier SQLite de l'application.
 *
 * <p>La sauvegarde utilise {@code VACUUM INTO}, qui produit une copie
 * cohérente et compacte même si la base est ouverte en mode WAL. La
 * restauration valide d'abord le fichier choisi, crée une sauvegarde de
 * sécurité, ferme le pool HikariCP, remplace le fichier de base puis
 * réinitialise l'application. Aucune donnée n'est supprimée avant que la
 * sauvegarde de sécurité n'ait été écrite.</p>
 */
public final class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final BackupService INSTANCE = new BackupService();

    private final SettingService settings = SettingService.get();

    private BackupService() {
    }

    public static BackupService get() {
        return INSTANCE;
    }

    /** Résultat d'une restauration : sauvegarde de sécurité + fichier restauré. */
    public record RestoreResult(Path safetyBackup, Path restoredFile) {
    }

    /** Dossier par défaut des sauvegardes : {@code %APPDATA%/TechShopManager/backups}. */
    public Path defaultDirectory() {
        String configure = settings.get("backup.dir", "");
        if (configure != null && !configure.isBlank()) {
            return Path.of(configure.trim()).toAbsolutePath().normalize();
        }
        return AppConfig.backupDirectory();
    }

    /** Crée une copie SQLite horodatée dans le dossier demandé. */
    public Path backupNow(Path directory) throws IOException {
        Path dir = directory == null ? defaultDirectory() : directory.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path fichier = dir.resolve("techshop-" + LocalDateTime.now().format(STAMP) + ".db");
        // Deux sauvegardes dans la même seconde ne doivent pas s'écraser.
        int suffixe = 1;
        while (Files.exists(fichier)) {
            fichier = dir.resolve("techshop-" + LocalDateTime.now().format(STAMP)
                    + "-" + suffixe++ + ".db");
        }
        creerCopie(fichier);
        if (!Files.isRegularFile(fichier) || Files.size(fichier) == 0) {
            throw new IOException("La sauvegarde n'a pas été créée. Vérifiez les droits du dossier "
                    + dir + ".");
        }
        verifierFichier(fichier);
        log.info("Sauvegarde SQLite créée : {}", fichier);
        return fichier;
    }

    /**
     * Restaure un fichier SQLite. Une sauvegarde de sécurité est d'abord créée
     * dans le dossier par défaut pour pouvoir revenir en arrière en cas d'erreur.
     */
    public RestoreResult restore(Path fichier) throws IOException {
        return restore(fichier, defaultDirectory());
    }

    /**
     * Restaure un fichier SQLite avec un dossier de sécurité explicite. Le
     * fichier est validé avant toute modification de la base courante.
     */
    public RestoreResult restore(Path fichier, Path dossierSecurite) throws IOException {
        if (fichier == null || !Files.isRegularFile(fichier)) {
            throw new IOException("Fichier de sauvegarde introuvable.");
        }
        Path source = fichier.toAbsolutePath().normalize();
        verifierFichier(source);

        Path cible = AppConfig.databasePath();
        if (source.equals(cible)) {
            throw new IOException("Le fichier sélectionné est déjà la base de données active.");
        }

        Path securite = backupNow(dossierSecurite);
        Path sauvegardeCourante = null;
        try {
            // Conserve une copie de secours du fichier courant avant remplacement.
            sauvegardeCourante = cible.resolveSibling(cible.getFileName() + ".restore-tmp");
            if (Files.exists(cible)) {
                Files.copy(cible, sauvegardeCourante, StandardCopyOption.REPLACE_EXISTING);
            }

            DatabaseManager.shutdown();
            Files.createDirectories(cible.getParent());
            Files.copy(source, cible, StandardCopyOption.REPLACE_EXISTING);
            supprimerFichiersAnnexes(cible);

            DatabaseManager.init();
            if (!DatabaseManager.isReady()) {
                throw new IOException("La base restaurée n'a pas pu être ouverte : "
                        + DatabaseManager.getLastError());
            }
            log.info("Base SQLite restaurée depuis {}", source);
            return new RestoreResult(securite, source);
        } catch (IOException | RuntimeException e) {
            // Tentative de retour à l'état précédent.
            try {
                DatabaseManager.shutdown();
                if (sauvegardeCourante != null && Files.isRegularFile(sauvegardeCourante)) {
                    Files.copy(sauvegardeCourante, cible, StandardCopyOption.REPLACE_EXISTING);
                    supprimerFichiersAnnexes(cible);
                }
                DatabaseManager.init();
            } catch (Exception rollback) {
                log.error("Retour arrière après restauration impossible", rollback);
            }
            throw e instanceof IOException io ? io
                    : new IOException("Restauration impossible : " + e.getMessage(), e);
        } finally {
            if (sauvegardeCourante != null) {
                try {
                    Files.deleteIfExists(sauvegardeCourante);
                } catch (IOException ignored) {
                    // rien
                }
            }
        }
    }

    /** Sauvegarde silencieuse à la fermeture si l'option est activée. */
    public void autoBackupOnExit() {
        if (!Boolean.parseBoolean(settings.get("backup.auto_on_exit", "false"))) {
            return;
        }
        try {
            Path fichier = backupNow(defaultDirectory());
            log.info("Sauvegarde automatique à la fermeture : {}", fichier);
        } catch (Exception e) {
            log.warn("Sauvegarde automatique impossible : {}", e.getMessage());
        }
    }

    /** La sauvegarde SQLite est toujours disponible (aucun outil externe requis). */
    public boolean toolsAvailable() {
        return true;
    }

    // ---------------------------------------------------------------- interne

    /** Écrit une copie cohérente de la base avec {@code VACUUM INTO}. */
    private static void creerCopie(Path destination) throws IOException {
        String sql = "VACUUM INTO ?";
        try (Connection c = DriverManager.getConnection(AppConfig.jdbcUrl())) {
            // Le Statement doit être fermé avant VACUUM, sinon SQLite signale
            // « cannot VACUUM - SQL statements in progress ».
            try (Statement pragma = c.createStatement()) {
                pragma.execute("PRAGMA busy_timeout = 10000");
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, destination.toString());
                ps.execute();
            }
        } catch (SQLException e) {
            throw new IOException("Création de la sauvegarde impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie qu'un fichier est une base SQLite lisible et contient le schéma
     * attendu. Aucun fichier n'est modifié.
     */
    private static void verifierFichier(Path fichier) throws IOException {
        String url = "jdbc:sqlite:" + fichier.toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement()) {
            st.execute("PRAGMA query_only = ON");
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new IOException("Le fichier de sauvegarde est corrompu (integrity_check).");
                }
            }
            if (!tableExiste(c, "users") || !tableExiste(c, "schema_version")) {
                throw new IOException("Le fichier sélectionné n'est pas une sauvegarde "
                        + "TechShop Manager valide.");
            }
        } catch (SQLException e) {
            throw new IOException("Fichier SQLite illisible : " + e.getMessage(), e);
        }
    }

    private static boolean tableExiste(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Supprime les fichiers WAL/SHM devenus obsolètes après remplacement. */
    private static void supprimerFichiersAnnexes(Path base) {
        for (String suffixe : new String[]{"-wal", "-shm"}) {
            try {
                Files.deleteIfExists(Path.of(base.toString() + suffixe));
            } catch (IOException e) {
                log.warn("Suppression de {} impossible : {}", base + suffixe, e.getMessage());
            }
        }
    }
}
