package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.ActivityLog;
import com.techshop.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès au journal d'activité (SPEC §5, §6). Insertion d'une part, recherche
 * filtrable d'autre part (utilisateur, action, entité, période, texte libre).
 */
public class ActivityLogDao {

    /**
     * Filtres de recherche du journal.
     *
     * @param userId  identifiant utilisateur (null = tous)
     * @param action  code d'action (null = toutes)
     * @param entity  table concernée (null = toutes)
     * @param du      début de période inclus (null = pas de borne)
     * @param au      fin de période incluse (null = pas de borne)
     * @param texte   recherche libre sur le détail ou l'identifiant (null = aucun)
     * @param limite  nombre maximal de lignes
     */
    public record Filter(Integer userId, String action, String entity,
                         LocalDate du, LocalDate au, String texte, int limite) {

        public static Filter empty() {
            return new Filter(null, null, null, null, null, null, 300);
        }
    }

    /** Insère une entrée de journal. */
    public void insert(Integer userId, String action, String entity, Integer entityId,
                       String details, String ip) throws SQLException {
        String sql = "INSERT INTO activity_log (user_id, action, entity, entity_id, details, ip) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, action);
            ps.setString(3, entity);
            if (entityId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, entityId);
            }
            ps.setString(5, details);
            ps.setString(6, ip);
            ps.executeUpdate();
        }
    }

    /** Recherche filtrée, du plus récent au plus ancien. */
    public List<ActivityLog> search(Filter f) throws SQLException {
        Filter filtre = f == null ? Filter.empty() : f;
        StringBuilder sql = new StringBuilder("SELECT a.id, a.user_id, u.username, a.action, "
                + "a.entity, a.entity_id, a.details, a.ip, a.created_at "
                + "FROM activity_log a LEFT JOIN users u ON u.id = a.user_id WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (filtre.userId() != null) {
            sql.append("AND a.user_id = ? ");
            params.add(filtre.userId());
        }
        if (filtre.action() != null && !filtre.action().isBlank()) {
            sql.append("AND a.action = ? ");
            params.add(filtre.action());
        }
        if (filtre.entity() != null && !filtre.entity().isBlank()) {
            sql.append("AND a.entity = ? ");
            params.add(filtre.entity());
        }
        if (filtre.du() != null) {
            sql.append("AND a.created_at >= ? ");
            params.add(Timestamp.valueOf(filtre.du().atStartOfDay()));
        }
        if (filtre.au() != null) {
            sql.append("AND a.created_at < ? ");
            params.add(Timestamp.valueOf(filtre.au().plusDays(1).atStartOfDay()));
        }
        if (filtre.texte() != null && !filtre.texte().isBlank()) {
            sql.append("AND (a.details LIKE ? OR u.username LIKE ?) ");
            String like = "%" + filtre.texte().trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY a.created_at DESC, a.id DESC LIMIT ?");
        params.add(Math.max(1, filtre.limite()));

        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ActivityLog> liste = new ArrayList<>();
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    int uid = rs.getInt("user_id");
                    Integer userId = rs.wasNull() ? null : uid;
                    int eid = rs.getInt("entity_id");
                    Integer entityId = rs.wasNull() ? null : eid;
                    liste.add(new ActivityLog(
                            rs.getLong("id"),
                            userId,
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("entity"),
                            entityId,
                            rs.getString("details"),
                            rs.getString("ip"),
                            ts == null ? null : ts.toLocalDateTime()));
                }
                return liste;
            }
        }
    }

    /** Valeurs distinctes d'action présentes dans le journal (liste de filtres). */
    public List<String> actionsDistinctes() throws SQLException {
        List<String> actions = new ArrayList<>();
        try (Connection c = DatabaseManager.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT action FROM activity_log ORDER BY action")) {
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
        }
        return actions;
    }

    /** Utilisateur (id + nom) pour les listes déroulantes du journal. */
    public List<UtilisateurOption> utilisateurs() throws SQLException {
        List<UtilisateurOption> liste = new ArrayList<>();
        try (Connection c = DatabaseManager.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, username, full_name, "
                             + "(SELECT code FROM roles WHERE id = users.role_id) AS role_code "
                             + "FROM users ORDER BY full_name")) {
            while (rs.next()) {
                liste.add(new UtilisateurOption(rs.getInt("id"), rs.getString("username"),
                        rs.getString("full_name"), Role.valueOf(rs.getString("role_code"))));
            }
        }
        return liste;
    }

    /** Option d'utilisateur (combobox de filtre). */
    public record UtilisateurOption(int id, String username, String fullName, Role role) {
        @Override
        public String toString() {
            return fullName + " (" + username + ")";
        }
    }
}
