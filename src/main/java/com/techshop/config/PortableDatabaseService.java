package com.techshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pilote le serveur MariaDB livré avec l'installeur lorsqu'il est présent.
 * Sans runtime embarqué, l'application conserve le repli vers un serveur
 * externe configuré et affiche un diagnostic explicite.
 */
public final class PortableDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(PortableDatabaseService.class);
    private static Process process;

    private PortableDatabaseService() {
    }

    public static synchronized boolean startIfAvailable() {
        if (!isWindows()) {
            return false;
        }
        Path executable = executable();
        Path data = AppPaths.portableMariaDbDirectory().resolve("data");
        if (!Files.isRegularFile(executable) || !Files.isDirectory(data.resolve("mysql"))) {
            log.info("Runtime MariaDB portable absent ; utilisation du serveur configuré.");
            return false;
        }
        if (process != null && process.isAlive()) {
            return true;
        }
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.add("--datadir=" + data.toAbsolutePath());
        command.add("--bind-address=127.0.0.1");
        command.add("--port=" + AppConfig.dbPort());
        command.add("--skip-name-resolve");
        command.add("--console");
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            log.info("Runtime MariaDB portable démarré sur 127.0.0.1:{}.", AppConfig.dbPort());
            return true;
        } catch (IOException e) {
            log.error("Démarrage de MariaDB portable impossible.", e);
            process = null;
            return false;
        }
    }

    public static synchronized void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        process = null;
    }

    public static Path executable() {
        Path base = AppPaths.portableMariaDbDirectory();
        return base.resolve("bin").resolve("mariadbd.exe");
    }

    public static boolean isAvailable() {
        Path data = AppPaths.portableMariaDbDirectory().resolve("data");
        return Files.isRegularFile(executable()) && Files.isDirectory(data.resolve("mysql"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
