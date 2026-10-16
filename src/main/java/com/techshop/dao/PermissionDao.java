package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.Permission;
import com.techshop.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès à la matrice de permissions (table « permissions », SPEC §3).
 * Une ligne par couple (rôle, module) ; les cinq droits sont des booléens.
 */
public class PermissionDao {

    /** Charge toute la matrice, groupée par rôle puis par module. */
    public Map<Role, Map<String, Permission>> loadAll() throws SQLException {
        String sql = "SELECT r.code AS role_code, p.module, p.can_view, p.can_create, "
                + "p.can_edit, p.can_delete, p.can_validate "
                + "FROM permissions p JOIN roles r ON r.id = p.role_id";
        Map<Role, Map<String, Permission>> matrice = new EnumMap<>(Role.class);
        try (Connection c = DatabaseManager.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Role role = Role.valueOf(rs.getString("role_code"));
                Permission perm = new Permission(rs.getString("module"),
                        rs.getInt("can_view") == 1, rs.getInt("can_create") == 1,
                        rs.getInt("can_edit") == 1, rs.getInt("can_delete") == 1,
                        rs.getInt("can_validate") == 1);
                matrice.computeIfAbsent(role, k -> new LinkedHashMap<>()).put(perm.module(), perm);
            }
        }
        return matrice;
    }

    /** Charge la matrice d'un seul rôle. */
    public Map<String, Permission> loadByRole(Role role) throws SQLException {
        String sql = "SELECT p.module, p.can_view, p.can_create, p.can_edit, p.can_delete, "
                + "p.can_validate FROM permissions p JOIN roles r ON r.id = p.role_id "
                + "WHERE r.code = ?";
        Map<String, Permission> perms = new LinkedHashMap<>();
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Permission perm = new Permission(rs.getString("module"),
                            rs.getInt("can_view") == 1, rs.getInt("can_create") == 1,
                            rs.getInt("can_edit") == 1, rs.getInt("can_delete") == 1,
                            rs.getInt("can_validate") == 1);
                    perms.put(perm.module(), perm);
                }
            }
        }
        return perms;
    }

    /**
     * Remplace intégralement la matrice d'un rôle (transaction :
     * suppression puis réinsertion des lignes fournies).
     */
    public void replaceRole(Role role, List<Permission> permissions) throws SQLException {
        try (Connection c = DatabaseManager.connection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int roleId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM roles WHERE code = ?")) {
                    ps.setString(1, role.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Rôle inconnu : " + role);
                        }
                        roleId = rs.getInt(1);
                    }
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM permissions WHERE role_id = ?")) {
                    del.setInt(1, roleId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO permissions (role_id, module, can_view, can_create, "
                                + "can_edit, can_delete, can_validate) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (Permission p : permissions) {
                        ins.setInt(1, roleId);
                        ins.setString(2, p.module());
                        ins.setInt(3, p.canView() ? 1 : 0);
                        ins.setInt(4, p.canCreate() ? 1 : 0);
                        ins.setInt(5, p.canEdit() ? 1 : 0);
                        ins.setInt(6, p.canDelete() ? 1 : 0);
                        ins.setInt(7, p.canValidate() ? 1 : 0);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    /** Modules connus de la matrice (union des modules présents en base). */
    public List<String> modules() throws SQLException {
        List<String> modules = new ArrayList<>();
        try (Connection c = DatabaseManager.connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT module FROM permissions ORDER BY module")) {
            while (rs.next()) {
                modules.add(rs.getString(1));
            }
        }
        return modules;
    }
}
