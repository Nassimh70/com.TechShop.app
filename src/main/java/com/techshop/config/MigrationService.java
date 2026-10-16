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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Migrations de schéma versionnées (SPEC §5).
 * <p>
 * Au démarrage : crée la table {@code schema_version} si besoin, découvre les
 * scripts {@code db/V###__description.sql} du classpath (dans l'ordre des
 * versions) et applique uniquement ceux qui n'ont jamais été exécutés.
 * Chaque script est enregistré (version, description, empreinte SHA-256,
 * horodatage, durée). Compatible MySQL 5.7+ / MariaDB : aucune instruction
 * exclusive à MySQL 8 (les DDL déclenchent un commit implicite, le suivi se
 * fait donc instruction par instruction).
 */
public final class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    /** Nom du répertoire des scripts dans les ressources. */
    private static final String DOSSIER = "db";
    /** Convention de nommage : V001__schema.sql */
    private static final Pattern NOM = Pattern.compile("V\\d+__[A-Za-z0-9_-]+\\.sql");
    /** Repli si le classpath n'expose pas le répertoire (JAR packagé). */
    private static final List<String> REPLI = List.of("V001__schema.sql", "V002__initial_data.sql",
            "V003__product_seed.sql", "V004__phase3_data.sql");

    public record Applied(String version, String description, long millis) {
    }

    private MigrationService() {
    }

    /**
     * Applique les scripts manquants.
     *
     * @return la liste des scripts effectivement exécutés (vide si à jour)
     */
    public static List<Applied> apply(DataSource ds) throws SQLException {
        List<Applied> appliques = new ArrayList<>();
        try (Connection c = ds.getConnection()) {
            ensureVersionTable(c);
            Set<String> deja = versionsAppliquees(c);
            for (String script : discoverScripts()) {
                String version = version(script);
                if (deja.contains(version)) {
                    continue;
                }
                String sql = lire(script);
                long debut = System.currentTimeMillis();
                executer(c, sql, script);
                long duree = System.currentTimeMillis() - debut;
                enregistrer(c, version, description(script), checksum(sql), script, duree);
                appliques.add(new Applied(version, description(script), duree));
                log.info("Migration {} appliquée en {} ms", script, duree);
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

    static String version(String script) {
        int sep = script.indexOf("__");
        String v = sep > 0 ? script.substring(1, sep) : script;
        // Normalise « 001 » -> « 001 » (conserve les zéros significatifs)
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
                    + "version VARCHAR(20) NOT NULL PRIMARY KEY, "
                    + "description VARCHAR(255) NULL, "
                    + "checksum VARCHAR(64) NULL, "
                    + "script VARCHAR(255) NULL, "
                    + "execution_ms INT NULL, "
                    + "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
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
     * apostrophes ('') et les échappements, ignore les commentaires
     * {@code -- ...} et {@code /* ... *}{@code /}.
     */
    static List<String> splitStatements(String script) {
        List<String> instructions = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        boolean dansChaine = false;
        boolean dansLigne = false;
        boolean dansBloc = false;
        char quote = 0;

        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            char suivant = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (dansLigne) {
                if (ch == '\n') {
                    dansLigne = false;
                    courante.append('\n');
                }
                continue;
            }
            if (dansBloc) {
                if (ch == '*' && suivant == '/') {
                    dansBloc = false;
                    i++;
                }
                continue;
            }
            if (dansChaine) {
                courante.append(ch);
                if (ch == '\\' && suivant != '\0') {
                    courante.append(suivant);
                    i++;
                } else if (ch == quote) {
                    if (suivant == quote) { // apostrophe doublée
                        courante.append(suivant);
                        i++;
                    } else {
                        dansChaine = false;
                    }
                }
                continue;
            }
            // Hors chaîne / commentaire
            if (ch == '-' && suivant == '-') {
                dansLigne = true;
                i++;
                continue;
            }
            if (ch == '/' && suivant == '*') {
                dansBloc = true;
                i++;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                dansChaine = true;
                quote = ch;
                courante.append(ch);
                continue;
            }
            if (ch == ';') {
                instructions.add(courante.toString());
                courante.setLength(0);
                continue;
            }
            courante.append(ch);
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
