package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class ReportDao {
    public record DayPoint(LocalDate date, long count, BigDecimal total, BigDecimal average) {}
    public record ProdPoint(String name, String ref, long qty, BigDecimal total) {}
    public record UserPoint(String user, long count, BigDecimal total) {}
    public record StockMv(String product, String ref, long in, long out, long balance) {}
    public record RepSum(String status, long count, BigDecimal quoted, BigDecimal finalAmount) {}
    public record CustPoint(String name, String phone, long orders, BigDecimal total, BigDecimal balance) {}
    public record FinPoint(LocalDate date, BigDecimal in, BigDecimal out, BigDecimal net) {}

    public List<DayPoint> salesByDay(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(s.sale_date) d, COUNT(*) c, COALESCE(SUM(s.total),0) t, COALESCE(AVG(s.total),0) a FROM sales s WHERE DATE(s.sale_date) BETWEEN ? AND ? GROUP BY d ORDER BY d";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<DayPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new DayPoint(LocalDate.parse(rs.getString(1).substring(0, 10)), rs.getLong(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
                }
                return list;
            }
        }
    }

    public List<ProdPoint> topProducts(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT p.name, p.ref, SUM(si.quantity) q, COALESCE(SUM(si.total),0) t FROM sale_items si JOIN products p ON p.id=si.product_id JOIN sales s ON s.id=si.sale_id WHERE DATE(s.sale_date) BETWEEN ? AND ? GROUP BY p.id,p.name,p.ref ORDER BY q DESC LIMIT 10";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<ProdPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new ProdPoint(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4)));
                }
                return list;
            }
        }
    }

    public List<UserPoint> salesByUser(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(u.full_name,u.username,'—') un, COUNT(*) c, COALESCE(SUM(s.total),0) t FROM sales s LEFT JOIN users u ON u.id=s.user_id WHERE DATE(s.sale_date) BETWEEN ? AND ? GROUP BY un ORDER BY t DESC";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<UserPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new UserPoint(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3)));
                }
                return list;
            }
        }
    }

    public List<StockMv> stockMovements(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT p.name,p.ref, "
                + "COALESCE(SUM(CASE WHEN sm.type='ENTREE' THEN sm.quantity ELSE 0 END),0) inq, "
                + "COALESCE(SUM(CASE WHEN sm.type IN ('SORTIE','VENTE','PERTE') "
                + "THEN ABS(sm.quantity) ELSE 0 END),0) outq, p.quantity bal "
                + "FROM stock_movements sm JOIN products p ON p.id=sm.product_id "
                + "WHERE DATE(sm.movement_date) BETWEEN ? AND ? "
                + "GROUP BY p.id,p.name,p.ref,p.quantity ORDER BY p.name";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<StockMv> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new StockMv(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
                }
                return list;
            }
        }
    }

    public List<RepSum> repairsSummary(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT rt.status, COUNT(*) c, COALESCE(SUM(rt.quoted_amount),0) q, "
                + "COALESCE(SUM(rt.total),0) f FROM repair_tickets rt "
                + "WHERE DATE(rt.created_at) BETWEEN ? AND ? GROUP BY rt.status ORDER BY c DESC";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<RepSum> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new RepSum(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
                }
                return list;
            }
        }
    }

    public List<CustPoint> topCustomers(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT c.full_name, c.phone, COUNT(DISTINCT s.id) ord, COALESCE(SUM(s.total),0) st, c.balance FROM sales s JOIN customers c ON c.id=s.customer_id WHERE DATE(s.sale_date) BETWEEN ? AND ? GROUP BY c.id,c.full_name,c.phone,c.balance ORDER BY st DESC LIMIT 20";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<CustPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new CustPoint(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4), rs.getBigDecimal(5)));
                }
                return list;
            }
        }
    }

    public List<FinPoint> financesDaily(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT inv.dt AS dt, COALESCE(inv.inm,0) inm, COALESCE(outv.outm,0) outm, "
                + "COALESCE(inv.inm,0)-COALESCE(outv.outm,0) net "
                + "FROM (SELECT DATE(s.sale_date) dt, SUM(s.total) inm FROM sales s "
                + "WHERE DATE(s.sale_date) BETWEEN ? AND ? GROUP BY dt) inv "
                + "LEFT JOIN (SELECT DATE(cm.movement_date) dt, "
                + "SUM(CASE WHEN cm.type='SORTIE' THEN cm.amount ELSE 0 END) outm "
                + "FROM cash_movements cm WHERE cm.cash_session_id IN "
                + "(SELECT id FROM cash_sessions cs WHERE DATE(cs.opened_at) BETWEEN ? AND ?) "
                + "AND cm.type='SORTIE' GROUP BY dt) outv ON inv.dt=outv.dt "
                + "UNION SELECT outv.dt, COALESCE(inv.inm,0), COALESCE(outv.outm,0), "
                + "COALESCE(inv.inm,0)-COALESCE(outv.outm,0) "
                + "FROM (SELECT DATE(cm.movement_date) dt, "
                + "SUM(CASE WHEN cm.type='SORTIE' THEN cm.amount ELSE 0 END) outm "
                + "FROM cash_movements cm WHERE cm.cash_session_id IN "
                + "(SELECT id FROM cash_sessions cs WHERE DATE(cs.opened_at) BETWEEN ? AND ?) "
                + "AND cm.type='SORTIE' GROUP BY dt) outv "
                + "LEFT JOIN (SELECT DATE(s.sale_date) dt, SUM(s.total) inm FROM sales s "
                + "WHERE DATE(s.sale_date) BETWEEN ? AND ? GROUP BY dt) inv ON outv.dt=inv.dt "
                + "WHERE inv.dt IS NULL ORDER BY 1";
        try (Connection c = DatabaseManager.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setString(3, from.toString());
            ps.setString(4, to.toString());
            ps.setString(5, from.toString());
            ps.setString(6, to.toString());
            ps.setString(7, from.toString());
            ps.setString(8, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<FinPoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new FinPoint(LocalDate.parse(rs.getString(1).substring(0, 10)), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
                }
                return list;
            }
        }
    }
}