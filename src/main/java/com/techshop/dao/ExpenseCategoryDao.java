package com.techshop.dao;

import com.techshop.config.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Catégories de dépenses (expense_categories) utilisées pour motiver les
 * sorties de caisse (SPEC §4.10).
 */
public class ExpenseCategoryDao {

    /** Option affichée dans la liste déroulante. */
    public record Option(int id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public List<Option> all() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name FROM expense_categories ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Option> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Option(rs.getInt("id"), rs.getString("name")));
                }
                return list;
            }
        }
    }
}
