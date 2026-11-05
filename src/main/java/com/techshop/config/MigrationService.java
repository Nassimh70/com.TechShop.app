package com.techshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Migrations de schéma versionnées pour SQLite.
 *
 * <p>Au démarrage : crée la table {@code schema_version} si besoin, découvre
 * les scripts {@code db/sqlite/V###__description.sql} du classpath (dans
 * l'ordre des versions) et applique uniquement ceux qui n'ont jamais été
 * exécutés. Chaque script est enregistré (version, description, empreinte
 * SHA-256, horodatage, durée) et exécuté dans une transaction : en cas
 * d'erreur, le script entier est annulé.</p>
 */
public final class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    /** Nom du répertoire des scripts SQLite dans les ressources. */
    private static final String DOSSIER = "db/sqlite";
    /** Convention de nommage : V001__schema.sql */
    private static final Pattern NOM = Pattern.compile("V\\d+__[A-Za-z0-9_-]+\\.sql");
    /** Repli si le classpath n'expose pas le répertoire (JAR packagé). */
    private static final List<String> REPLI = List.of(
            "V001__schema.sql",
            "V002__reference_data.sql",
            "V003__demo_data.sql");

    public record Applied(String version, String description, long millis) {
    }

    private MigrationService() {
    }

    /**
     * Applique les scripts manquants pour le profil courant.
     *
     * @return la liste des scripts effectivement exécutés (vide si à jour)
     */
    public static List<Applied> apply(DataSource ds) throws SQLException {
        return applyScripts(ds, scriptsForProfile());
    }

    /**
     * Applique uniquement le schéma (V001), sans les données de référence.
     * Utilisé par l'outil de migration MySQL → SQLite avant la copie des
     * données sources.
     */
    public static List<Applied> applySchemaOnly(DataSource ds) throws SQLException {
        List<String> scripts = discoverScripts().stream()
                .filter(script -> script.startsWith("V001__"))
                .toList();
        return applyScripts(ds, scripts);
    }

    /**
     * Enregistre une version comme déjà appliquée sans exécuter son script.
     * Utilisé après une migration externe qui a déjà copié les données.
     */
    public static void markApplied(DataSource ds, String version, String description,
                                   String script) throws SQLException {
        try (Connection c = ds.getConnection()) {
            ensureVersionTable(c);
            if (!versionsAppliquees(c).contains(version)) {
                enregistrer(c, version, description, null, script, 0);
            }
        }
    }

    private static List<Applied> applyScripts(DataSource ds, List<String> scripts) throws SQLException {
        List<Applied> appliques = new ArrayList<>();
        try (Connection c = ds.getConnection()) {
            ensureVersionTable(c);
            Set<String> deja = versionsAppliquees(c);
            for (String script : scripts) {
                String version = version(script);
                if (deja.contains(version)) {
                    continue;
                }
                String sql = lire(script);
                long debut = System.currentTimeMillis();
                boolean autoCommit = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    executer(c, sql, script);
                    long duree = System.currentTimeMillis() - debut;
                    enregistrer(c, version, description(script), checksum(sql), script, duree);
                    c.commit();
                    appliques.add(new Applied(version, description(script), duree));
                    log.info("Migration {} appliquée en {} ms", script, duree);
                } catch (SQLException e) {
                    try {
                        c.rollback();
                    } catch (SQLException rollback) {
                        e.addSuppressed(rollback);
                    }
                    throw e;
                } finally {
                    try {
                        c.setAutoCommit(autoCommit);
                    } catch (SQLException ignored) {
                        // la connexion sera rendue au pool de toute façon
                    }
                }
            }
        }
        if (appliques.isEmpty()) {
            log.info("Schéma à jour : aucune migration à appliquer");
        }
        return appliques;
    }

    // --------------------------------------------------------------- découverte

    /** Scripts à appliquer, triés par version croissante. */
    static List<String> discoverScripts() {
        URL dir = MigrationService.class.getResource("/" + DOSSIER);
        if (dir != null && "file".equals(dir.getProtocol())) {
            try {
                Path p = Path.of(dir.toURI());
                try (var flux = Files.list(p)) {
                    List<String> noms = flux
                            .map(x -> x.getFileName().toString())
                            .filter(n -> NOM.matcher(n).matches())
                            .sorted()
                            .toList();
                    if (!noms.isEmpty()) {
                        return noms;
                    }
                }
            } catch (Exception e) {
                log.debug("Découverte des scripts par répertoire impossible : {}", e.getMessage());
            }
        }
        return REPLI;
    }

    static List<String> scriptsForProfile() {
        List<String> scripts = discoverScripts();
        if (AppConfig.isDemoProfile()) {
            return scripts;
        }
        return scripts.stream()
                .filter(script -> !estSeedDemo(script))
                .toList();
    }

    static boolean estSeedDemo(String script) {
        return script.equals("V003__demo_data.sql");
    }

    static String version(String script) {
        int sep = script.indexOf("__");
        String v = sep > 0 ? script.substring(1, sep) : script;
        // Conserve les zéros significatifs (« 001 »)
        return v;
    }

    static String description(String script) {
        int sep = script.indexOf("__");
        String d = sep > 0 ? script.substring(sep + 2) : script;
        return d.replaceAll("\\.sql$", "").replace('_', ' ');
    }

    // --------------------------------------------------------------- exécution

    private static void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version TEXT NOT NULL PRIMARY KEY, "
                    + "description TEXT NULL, "
                    + "checksum TEXT NULL, "
                    + "script TEXT NULL, "
                    + "execution_ms INTEGER NULL, "
                    + "applied_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))"
                    + ")");
        }
    }

    private static Set<String> versionsAppliquees(Connection c) throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) {
                versions.add(rs.getString(1));
            }
        }
        return versions;
    }

    private static void executer(Connection c, String script, String nom) throws SQLException {
        for (String instruction : splitStatements(script)) {
            String s = instruction.trim();
            if (s.isEmpty()) {
                continue;
            }
            try (Statement st = c.createStatement()) {
                st.executeUpdate(s);
            } catch (SQLException e) {
                log.error("Échec de l'instruction du script {} : {}", nom, resumer(s));
                throw e;
            }
        }
    }

    private static void enregistrer(Connection c, String version, String description,
                                    String checksum, String script, long millis) throws SQLException {
        String sql = "INSERT INTO schema_version (version, description, checksum, script, execution_ms) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.setString(2, description);
            ps.setString(3, checksum);
            ps.setString(4, script);
            ps.setInt(5, (int) millis);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ lecture

    private static String lire(String script) throws SQLException {
        try (InputStream in = MigrationService.class.getResourceAsStream("/" + DOSSIER + "/" + script)) {
            if (in == null) {
                throw new SQLException("Script de migration introuvable : /" + DOSSIER + "/" + script);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Lecture impossible du script " + script, e);
        }
    }

    static String checksum(String contenu) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(contenu.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Découpe un script en instructions SQL : respecte les chaînes entre
     * apostrophes, guillemets et backticks, ignore les commentaires
     * {@code -- ...} et {@code /* ... *}{@code /}, et ne coupe pas à
     * l'intérieur d'un corps de trigger {@code BEGIN ... END}.
     */
    static List<String> splitStatements(String script) {
        List<String> instructions = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        boolean dansChaine = false;
        boolean dansLigne = false;
        boolean dansBloc = false;
        boolean dansTrigger = false;
        int profondeurBloc = 0;
        char quote = 0;

        for (int i = 0; i < script.length(); ) {
            char ch = script.charAt(i);
            char suivant = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (dansLigne) {
                if (ch == '\n') {
                    dansLigne = false;
                    courante.append('\n');
                }
                i++;
                continue;
            }
            if (dansBloc) {
                if (ch == '*' && suivant == '/') {
                    dansBloc = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (dansChaine) {
                courante.append(ch);
                if (ch == '\\' && suivant != '\0') {
                    courante.append(suivant);
                    i += 2;
                } else if (ch == quote) {
                    if (suivant == quote) { // apostrophe doublée
                        courante.append(suivant);
                        i += 2;
                    } else {
                        dansChaine = false;
                        i++;
                    }
                } else {
                    i++;
                }
                continue;
            }
            // Hors chaîne / commentaire
            if (ch == '-' && suivant == '-') {
                dansLigne = true;
                i += 2;
                continue;
            }
            if (ch == '/' && suivant == '*') {
                dansBloc = true;
                i += 2;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                dansChaine = true;
                quote = ch;
                courante.append(ch);
                i++;
                continue;
            }
            if (Character.isLetter(ch) || ch == '_') {
                int debut = i;
                while (i < script.length()) {
                    char c = script.charAt(i);
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        break;
                    }
                    i++;
                }
                String mot = script.substring(debut, i).toUpperCase(Locale.ROOT);
                courante.append(script, debut, i);
                if (dansTrigger) {
                    if ("BEGIN".equals(mot) || "CASE".equals(mot)) {
                        profondeurBloc++;
                    } else if ("END".equals(mot)) {
                        profondeurBloc = Math.max(0, profondeurBloc - 1);
                    }
                } else if ("TRIGGER".equals(mot)) {
                    dansTrigger = true;
                    profondeurBloc = 0;
                }
                continue;
            }
            if (ch == ';') {
                if (dansTrigger && profondeurBloc > 0) {
                    courante.append(';');
                } else {
                    instructions.add(courante.toString());
                    courante.setLength(0);
                    dansTrigger = false;
                    profondeurBloc = 0;
                }
                i++;
                continue;
            }
            courante.append(ch);
            i++;
        }
        if (!courante.toString().isBlank()) {
            instructions.add(courante.toString());
        }
        return instructions;
    }

    private static String resumer(String sql) {
        String uneLigne = sql.replaceAll("\\s+", " ").trim();
        return uneLigne.length() > 120 ? uneLigne.substring(0, 120) + "…" : uneLigne;
    }
}
