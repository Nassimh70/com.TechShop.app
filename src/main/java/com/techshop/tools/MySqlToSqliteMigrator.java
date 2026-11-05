package com.techshop.tools;

import com.techshop.config.MigrationService;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Outil ponctuel de migration MySQL/MariaDB vers SQLite.
 *
 * <p>Il crée un nouveau fichier SQLite, applique le schéma V001, copie les
 * données table par table en conservant les identifiants et les relations,
 * puis vérifie le nombre de lignes et l'intégrité référentielle. La base
 * source n'est jamais modifiée.</p>
 *
 * <p>Exécution (profil Maven {@code mysql-migration}) :</p>
 * <pre>
 * mvn -Pmysql-migration compile exec:java \
 *   -Dexec.args="--source-url jdbc:mariadb://localhost:3306/techshop \
 *                --source-user root --source-password secret \
 *                --target C:\Users\moi\AppData\Roaming\TechShopManager\data\techshop.db"
 * </pre>
 *
 * <p>Le fichier cible doit être absent ou vide : l'outil refuse d'écraser une
 * base existante.</p>
 */
public final class MySqlToSqliteMigrator {

    /** Tables techniques de MySQL qui n'ont pas d'équivalent SQLite. */
    private static final Set<String> IGNOREES = Set.of("schema_version", "permissions", "roles");

    private MySqlToSqliteMigrator() {
    }

    public static void main(String[] args) {
        try {
            new MySqlToSqliteMigrator().run(args);
        } catch (Exception e) {
            System.err.println("ÉCHEC DE LA MIGRATION : " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run(String[] args) throws Exception {
        Map<String, String> options = options(args);
        String sourceUrl = obligatoire(options, "source-url",
                "jdbc:mariadb://localhost:3306/techshop");
        String sourceUser = obligatoire(options, "source-user", "root");
        String sourcePassword = options.getOrDefault("source-password", "");
        String targetValue = obligatoire(options, "target", null);
        Path target = Path.of(targetValue).toAbsolutePath().normalize();

        if (Files.exists(target) && Files.size(target) > 0) {
            throw new IllegalStateException("Le fichier cible existe déjà et n'est pas vide : "
                    + target + ". Choisissez un nouveau fichier ; aucune base n'est écrasée.");
        }
        Files.createDirectories(target.getParent());

        System.out.println("Source MySQL : " + sourceUrl);
        System.out.println("Cible SQLite : " + target);

        Class.forName("org.mariadb.jdbc.Driver");
        Class.forName("org.sqlite.JDBC");

        try (Connection source = DriverManager.getConnection(sourceUrl, sourceUser, sourcePassword)) {
            source.setReadOnly(true);
            verifierSource(source);
            List<String> tables = tablesSource(source);
            System.out.println(tables.size() + " table(s) source détectée(s).");

            creerSchema(target);
            CopieResultat resultat = copier(source, target, tables);
            verifier(target, source, resultat);
            marquerDonneesDejaPresentes(target);

            System.out.println();
            System.out.println("MIGRATION TERMINÉE AVEC SUCCÈS");
            System.out.println("Lignes copiées : " + resultat.total());
            System.out.println("Base SQLite : " + target);
            System.out.println("La base MySQL source n'a pas été modifiée.");
        }
    }

    // --------------------------------------------------------------- source

    private static void verifierSource(Connection source) throws SQLException {
        for (String table : List.of("users", "products", "sales")) {
            if (!tableExiste(source, table)) {
                throw new IllegalStateException("Table source obligatoire absente : " + table);
            }
        }
        System.out.println("Connexion MySQL établie et schéma source reconnu.");
    }

    private static List<String> tablesSource(Connection source) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = source.getMetaData();
        String catalogue = source.getCatalog();
        try (ResultSet rs = meta.getTables(catalogue, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String nom = rs.getString("TABLE_NAME");
                if (nom != null && !IGNOREES.contains(nom.toLowerCase(Locale.ROOT))) {
                    tables.add(nom);
                }
            }
        }
        tables.sort(String.CASE_INSENSITIVE_ORDER);
        return tables;
    }

    // --------------------------------------------------------------- cible

    private static void creerSchema(Path target) throws SQLException {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + target.toString().replace('\\', '/'));
        List<MigrationService.Applied> appliquees = MigrationService.applySchemaOnly(ds);
        System.out.println("Schéma SQLite appliqué (" + appliquees.size() + " script(s)).");
    }

    private static CopieResultat copier(Connection source, Path target, List<String> tables)
            throws SQLException {
        String url = "jdbc:sqlite:" + target.toString().replace('\\', '/')
                + "?foreign_keys=off&busy_timeout=10000&date_class=TEXT"
                + "&date_string_format=yyyy-MM-dd HH:mm:ss";
        CopieResultat resultat = new CopieResultat();
        try (Connection cible = DriverManager.getConnection(url)) {
            // Les PRAGMA doivent être exécutés dans un Statement fermé avant
            // le commit : sinon SQLite signale « SQL statements in progress ».
            try (Statement pragma = cible.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = OFF");
                pragma.execute("PRAGMA journal_mode = WAL");
            }
            cible.setAutoCommit(false);
            try {
                for (String table : tables) {
                    copierTable(source, cible, table, resultat);
                }
                cible.commit();
            } catch (Exception e) {
                cible.rollback();
                throw e;
            }
        }
        return resultat;
    }

    private static void copierTable(Connection source, Connection cible, String table,
                                    CopieResultat resultat) throws SQLException {
        if (!tableExiste(cible, table)) {
            System.out.println("  - " + table + " : absente du schéma SQLite, ignorée.");
            return;
        }
        List<String> colonnesCible = colonnes(cible, table);
        List<String> colonnesSource = colonnes(source, table);
        List<String> communes = colonnesCible.stream()
                .filter(colonnesSource::contains)
                .toList();
        if (communes.isEmpty()) {
            System.out.println("  - " + table + " : aucune colonne commune, ignorée.");
            return;
        }

        String listeSource = String.join(", ",
                communes.stream().map(MySqlToSqliteMigrator::quoteSource).toList());
        String listeCible = String.join(", ",
                communes.stream().map(MySqlToSqliteMigrator::quoteTarget).toList());
        String marques = String.join(", ", java.util.Collections.nCopies(communes.size(), "?"));
        String select = "SELECT " + listeSource + " FROM " + quoteSource(table);
        String insert = "INSERT INTO " + quoteTarget(table) + " (" + listeCible + ") VALUES ("
                + marques + ")";

        long lignes = 0;
        try (PreparedStatement lecture = source.prepareStatement(select);
             ResultSet rs = lecture.executeQuery();
             PreparedStatement ecriture = cible.prepareStatement(insert)) {
            while (rs.next()) {
                for (int i = 1; i <= communes.size(); i++) {
                    ecriture.setObject(i, rs.getObject(i));
                }
                ecriture.addBatch();
                if (++lignes % 500 == 0) {
                    ecriture.executeBatch();
                }
            }
            ecriture.executeBatch();
        }
        resultat.ajouter(table, lignes);
        System.out.println("  - " + table + " : " + lignes + " ligne(s).");
    }

    // ---------------------------------------------------------- vérification

    private static void verifier(Path target, Connection source, CopieResultat resultat)
            throws SQLException {
        String url = "jdbc:sqlite:" + target.toString().replace('\\', '/');
        List<String> erreurs = new ArrayList<>();
        try (Connection cible = DriverManager.getConnection(url);
             Statement st = cible.createStatement()) {
            for (Map.Entry<String, Long> entree : resultat.parTable().entrySet()) {
                long attendu = compter(source, entree.getKey());
                long obtenu = compter(cible, entree.getKey());
                if (attendu != obtenu) {
                    erreurs.add(entree.getKey() + " : " + attendu + " attendu(s), "
                            + obtenu + " copié(s)");
                }
            }
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                while (rs.next()) {
                    erreurs.add("Clé étrangère invalide dans " + rs.getString(1)
                            + " (ligne " + rs.getString(2) + ")");
                }
            }
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                if (rs.next() && !"ok".equalsIgnoreCase(rs.getString(1))) {
                    erreurs.add("integrity_check : " + rs.getString(1));
                }
            }
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalStateException("Vérification échouée :\n - "
                    + String.join("\n - ", erreurs));
        }
        System.out.println("Vérification OK : nombres de lignes identiques et intégrité référentielle valide.");
    }

    private static void marquerDonneesDejaPresentes(Path target) throws SQLException {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + target.toString().replace('\\', '/'));
        MigrationService.markApplied(ds, "002", "reference data", "V002__reference_data.sql");
        MigrationService.markApplied(ds, "003", "demo data", "V003__demo_data.sql");
        System.out.println("Les jeux de données de référence/démo sont marqués comme déjà présents.");
    }

    // -------------------------------------------------------------- utilitaires

    private static boolean tableExiste(Connection c, String table) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static List<String> colonnes(Connection c, String table) throws SQLException {
        List<String> colonnes = new ArrayList<>();
        boolean mysql = c.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("mysql")
                || c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mariadb");
        String tableSql = mysql ? quoteSource(table) : quoteTarget(table);
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + tableSql + " WHERE 1 = 0")) {
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                colonnes.add(meta.getColumnName(i));
            }
        }
        return colonnes;
    }

    private static long compter(Connection c, String table) throws SQLException {
        boolean mysql = c.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("mysql")
                || c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mariadb");
        String tableSql = mysql ? quoteSource(table) : quoteTarget(table);
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableSql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Identifiant SQLite (guillemets doubles). */
    private static String quoteTarget(String identifiant) {
        return "\"" + identifiant.replace("\"", "\"\"") + "\"";
    }

    /** Identifiant MySQL/MariaDB (backticks). */
    private static String quoteSource(String identifiant) {
        return "`" + identifiant.replace("`", "``") + "`";
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Argument inattendu : " + arg);
            }
            String cle = arg.substring(2);
            String valeur;
            int egal = cle.indexOf('=');
            if (egal >= 0) {
                valeur = cle.substring(egal + 1);
                cle = cle.substring(0, egal);
            } else {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Valeur manquante pour --" + cle);
                }
                valeur = args[++i];
            }
            options.put(cle, valeur);
        }
        return options;
    }

    private static String obligatoire(Map<String, String> options, String cle, String defaut) {
        String valeur = options.get(cle);
        if (valeur == null || valeur.isBlank()) {
            valeur = defaut;
        }
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Option obligatoire manquante : --" + cle);
        }
        return valeur;
    }

    private record CopieResultat(Map<String, Long> parTable) {
        CopieResultat() {
            this(new LinkedHashMap<>());
        }

        void ajouter(String table, long lignes) {
            parTable.put(table, lignes);
        }

        long total() {
            return parTable.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
