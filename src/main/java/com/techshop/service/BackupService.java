package com.techshop.service;

import com.techshop.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sauvegarde et restauration de la base MySQL/MariaDB via les utilitaires
 * {@code mysqldump} et {@code mysql} fournis avec XAMPP.
 *
 * <p>Les exécutables sont cherchés dans le dossier configuré
 * ({@code backup.mysql_bin}), puis dans le {@code PATH}. Une sauvegarde de
 * sécurité est créée automatiquement avant toute restauration.</p>
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

    /** Dossier par défaut des sauvegardes : {@code data/backups}. */
    public Path defaultDirectory() {
        String configure = settings.get("backup.dir", "");
        if (configure != null && !configure.isBlank()) {
            return Path.of(configure.trim());
        }
        return Path.of(System.getProperty("user.dir"), "data", "backups");
    }

    /** Crée un dump SQL horodaté dans le dossier demandé. */
    public Path backupNow(Path directory) throws IOException, InterruptedException {
        return backupNow(directory, null);
    }

    /**
     * Crée un dump SQL dans le dossier demandé, en utilisant éventuellement un
     * dossier MySQL explicite (utile avant l'enregistrement des paramètres).
     */
    public Path backupNow(Path directory, String mysqlBin) throws IOException, InterruptedException {
        Path dir = directory == null ? defaultDirectory() : directory;
        Files.createDirectories(dir);
        Path fichier = dir.resolve("techshop-" + LocalDateTime.now().format(STAMP) + ".sql");

        Path dump = executable("mysqldump", mysqlBin).orElseThrow(() -> new IOException(
                "mysqldump introuvable. Vérifiez le dossier MySQL (bin) dans les paramètres."));

        List<String> commande = new ArrayList<>();
        commande.add(dump.toString());
        commande.add("--host=" + AppConfig.dbHost());
        commande.add("--port=" + AppConfig.dbPort());
        commande.add("--user=" + AppConfig.dbUser());
        ajouterMotDePasse(commande);
        commande.add("--single-transaction");
        commande.add("--routines");
        commande.add("--events");
        commande.add("--databases");
        commande.add(AppConfig.dbName());
        commande.add("--result-file=" + fichier.toAbsolutePath());

        executer(commande, null);
        if (!Files.isRegularFile(fichier) || Files.size(fichier) == 0) {
            throw new IOException("La sauvegarde n'a pas été créée. Vérifiez les droits du dossier "
                    + dir.toAbsolutePath() + ".");
        }
        log.info("Sauvegarde créée : {}", fichier.toAbsolutePath());
        return fichier;
    }

    /**
     * Restaure un dump SQL. Une sauvegarde de sécurité est d'abord créée dans
     * le dossier par défaut pour pouvoir revenir en arrière en cas d'erreur.
     */
    public RestoreResult restore(Path fichier) throws IOException, InterruptedException {
        return restore(fichier, null);
    }

    /**
     * Restaure un dump SQL avec un dossier MySQL explicite. Une sauvegarde de
     * sécurité est d'abord créée dans le dossier par défaut.
     */
    public RestoreResult restore(Path fichier, String mysqlBin) throws IOException, InterruptedException {
        if (fichier == null || !Files.isRegularFile(fichier)) {
            throw new IOException("Fichier de sauvegarde introuvable.");
        }
        Path securite = backupNow(defaultDirectory(), mysqlBin);

        Path mysql = executable("mysql", mysqlBin).orElseThrow(() -> new IOException(
                "mysql introuvable. Vérifiez le dossier MySQL (bin) dans les paramètres."));

        List<String> commande = new ArrayList<>();
        commande.add(mysql.toString());
        commande.add("--host=" + AppConfig.dbHost());
        commande.add("--port=" + AppConfig.dbPort());
        commande.add("--user=" + AppConfig.dbUser());
        ajouterMotDePasse(commande);

        executer(commande, fichier);
        log.info("Base restaurée depuis {}", fichier.toAbsolutePath());
        return new RestoreResult(securite, fichier);
    }

    /** Sauvegarde silencieuse à la fermeture si l'option est activée. */
    public void autoBackupOnExit() {
        if (!Boolean.parseBoolean(settings.get("backup.auto_on_exit", "false"))) {
            return;
        }
        try {
            Path fichier = backupNow(defaultDirectory());
            log.info("Sauvegarde automatique à la fermeture : {}", fichier.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Sauvegarde automatique impossible : {}", e.getMessage());
        }
    }

    /** Indique si les deux utilitaires MySQL sont accessibles. */
    public boolean toolsAvailable() {
        return executable("mysqldump", null).isPresent() && executable("mysql", null).isPresent();
    }

    // ---------------------------------------------------------------- interne

    private Optional<Path> executable(String nom, String binExplicite) {
        String bin = binExplicite != null && !binExplicite.isBlank()
                ? binExplicite.trim() : settings.get("backup.mysql_bin", defaultMysqlBin());
        if (bin != null && !bin.isBlank()) {
            Path dossier = Path.of(bin.trim());
            for (String candidat : List.of(nom + ".exe", nom)) {
                Path fichier = dossier.resolve(candidat);
                if (Files.isRegularFile(fichier)) {
                    return Optional.of(fichier);
                }
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String element : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (element.isBlank()) {
                    continue;
                }
                for (String candidat : List.of(nom + ".exe", nom)) {
                    Path fichier = Path.of(element).resolve(candidat);
                    if (Files.isRegularFile(fichier)) {
                        return Optional.of(fichier);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String defaultMysqlBin() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "C:\\xampp\\mysql\\bin" : "/usr/bin";
    }

    private static void ajouterMotDePasse(List<String> commande) {
        String motDePasse = AppConfig.dbPassword();
        if (motDePasse != null && !motDePasse.isBlank()) {
            commande.add("--password=" + motDePasse);
        }
    }

    private static String executer(List<String> commande, Path entree)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(commande);
        builder.redirectErrorStream(true);
        if (entree != null) {
            builder.redirectInput(entree.toFile());
        }
        Process processus = builder.start();
        byte[] sortie = processus.getInputStream().readAllBytes();
        int code = processus.waitFor();
        String texte = new String(sortie, StandardCharsets.UTF_8).trim();
        if (code != 0) {
            throw new IOException("La commande MySQL a échoué (code " + code + ")"
                    + (texte.isBlank() ? "." : " : " + texte));
        }
        return texte;
    }
}
