package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.CustomerPayment;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerPaymentDao {

    private static final String SELECT = "SELECT cp.id, cp.customer_id, c.full_name customer_name, "
            + "cp.amount, cp.payment_method, cp.payment_date, cp.cash_session_id, cp.note, "
            + "cp.user_id, u.username FROM customer_payments cp "
            + "JOIN customers c ON c.id = cp.customer_id "
            + "JOIN users u ON u.id = cp.user_id ";

    public record Filter(Integer customerId, LocalDateTime from, LocalDateTime to) {}

    public List<CustomerPayment> search(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f != null) {
            if (f.customerId() != null) {
                sql.append("AND cp.customer_id = ? ");
                params.add(f.customerId());
            }
            if (f.from() != null) {
                sql.append("AND cp.payment_date >= ? ");
                params.add(Timestamp.valueOf(f.from()));
            }
            if (f.to() != null) {
                sql.append("AND cp.payment_date <= ? ");
                params.add(Timestamp.valueOf(f.to()));
            }
        }
        sql.append("ORDER BY cp.payment_date DESC, cp.id DESC");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<CustomerPayment> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public Optional<CustomerPayment> byId(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE cp.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Corrige la date (et la note) d'un règlement déjà enregistré. */
    public void updateDate(int id, LocalDateTime paymentDate, String note) throws SQLException {
        String sql = "UPDATE customer_payments SET payment_date = ?, note = ? WHERE id = ?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(paymentDate));
            ps.setString(2, note);
            ps.setInt(3, id);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Règlement client introuvable");
            }
        }
    }

    public int insert(CustomerPayment pmt) throws SQLException {
        String sql = "INSERT INTO customer_payments (customer_id, amount, payment_method, payment_date, cash_session_id, note, user_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pmt.getCustomerId());
            ps.setBigDecimal(2, pmt.getAmount());
            ps.setString(3, pmt.getPaymentMethod().name());
            ps.setTimestamp(4, pmt.getPaymentDate() == null ? new Timestamp(System.currentTimeMillis()) : Timestamp.valueOf(pmt.getPaymentDate()));
            if (pmt.getCashSessionId() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, pmt.getCashSessionId());
            ps.setString(6, pmt.getNote());
            ps.setInt(7, pmt.getUserId());
            if (ps.executeUpdate() == 0) throw new SQLException("Insertion paiement client échouée");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private CustomerPayment map(ResultSet rs) throws SQLException {
        CustomerPayment p = new CustomerPayment();
        p.setId(rs.getInt("id"));
        p.setCustomerId(rs.getInt("customer_id"));
        p.setCustomerName(rs.getString("customer_name"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setPaymentMethod(CustomerPayment.Method.valueOf(rs.getString("payment_method")));
        p.setPaymentDate(rs.getTimestamp("payment_date").toLocalDateTime());
        int cs = rs.getInt("cash_session_id");
        p.setCashSessionId(rs.wasNull() ? null : cs);
        p.setNote(rs.getString("note"));
        p.setUserId(rs.getInt("user_id"));
        p.setUsername(rs.getString("username"));
        return p;
    }
}
