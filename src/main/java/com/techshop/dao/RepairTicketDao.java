package com.techshop.dao;

import com.techshop.config.DatabaseManager;
import com.techshop.model.RepairStatus;
import com.techshop.model.RepairTicket;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Accès aux dossiers de réparation (SPEC §4.9, §5). JDBC pur, requêtes
 * préparées. La numérotation suit la convention « R-000001 » (préfixe + %06d).
 */
public class RepairTicketDao {

    private static final String SELECT = "SELECT r.id, r.number, r.customer_id, c.full_name AS customer_name, "
            + "c.phone AS customer_phone, r.device_type, r.brand, r.model, r.serial_or_imei, "
            + "r.device_password, r.accessories, r.physical_condition, r.reported_issue, r.diagnosis, "
            + "r.quoted_amount, "
            + "r.status, r.technician_id, u.full_name AS technician_name, r.received_at, r.promised_date, "
            + "r.completed_at, r.labor_cost, r.parts_total, r.discount, r.total, r.deposit_amount, "
            + "r.paid_amount, r.due_amount, r.warranty_days, r.internal_notes, r.created_at "
            + "FROM repair_tickets r "
            + "LEFT JOIN customers c ON c.id = r.customer_id "
            + "LEFT JOIN users u ON u.id = r.technician_id ";

    /** Filtres de la vue tableau (SPEC §4.9). */
    public record Filter(String query, String status, Integer technicianId, LocalDate from,
                         LocalDate to, Boolean late) {
    }

    public long nextNumber() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(MAX(CAST(SUBSTRING(number, 3) AS UNSIGNED)), 0) + 1 "
                             + "FROM repair_tickets WHERE number LIKE 'R-%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1;
            }
        }
    }

    public int insert(RepairTicket t) throws SQLException {
        if (t.getNumber() == null || t.getNumber().isBlank()) {
            t.setNumber("R-" + String.format("%06d", nextNumber()));
        }
        String sql = "INSERT INTO repair_tickets (number, customer_id, device_type, brand, model, "
                + "serial_or_imei, device_password, accessories, physical_condition, reported_issue, "
                + "diagnosis, status, technician_id, received_at, promised_date, completed_at, "
                + "labor_cost, parts_total, discount, total, deposit_amount, paid_amount, due_amount, "
                + "warranty_days, internal_notes, quoted_amount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getNumber());
            ps.setInt(2, t.getCustomerId());
            ps.setString(3, t.getDeviceType());
            ps.setString(4, t.getBrand());
            ps.setString(5, t.getModel());
            ps.setString(6, t.getSerialOrImei());
            ps.setString(7, t.getDevicePassword());
            ps.setString(8, t.getAccessories());
            ps.setString(9, t.getPhysicalCondition());
            ps.setString(10, t.getReportedIssue());
            ps.setString(11, t.getDiagnosis());
            ps.setString(12, t.getStatus() == null ? RepairStatus.RECU : t.getStatus());
            ps.setObject(13, t.getTechnicianId());
            ps.setTimestamp(14, Timestamp.valueOf(
                    t.getReceivedAt() == null ? LocalDateTime.now() : t.getReceivedAt()));
            ps.setDate(15, t.getPromisedDate() == null ? null : java.sql.Date.valueOf(t.getPromisedDate()));
            ps.setTimestamp(16, t.getCompletedAt() == null ? null : Timestamp.valueOf(t.getCompletedAt()));
            ps.setBigDecimal(17, nz(t.getLaborCost()));
            ps.setBigDecimal(18, nz(t.getPartsTotal()));
            ps.setBigDecimal(19, nz(t.getDiscount()));
            ps.setBigDecimal(20, nz(t.getTotal()));
            ps.setBigDecimal(21, nz(t.getDepositAmount()));
            ps.setBigDecimal(22, nz(t.getPaidAmount()));
            ps.setBigDecimal(23, nz(t.getDueAmount()));
            ps.setInt(24, t.getWarrantyDays());
            ps.setString(25, t.getInternalNotes());
            ps.setBigDecimal(26, nz(t.getQuotedAmount()));
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Échec insertion dossier de réparation");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                t.setId(id);
                return id;
            }
        }
    }

    /** Met à jour l'en-tête complet (hors statut et montants). */
    public void update(RepairTicket t) throws SQLException {
        String sql = "UPDATE repair_tickets SET device_type=?, brand=?, model=?, serial_or_imei=?, "
                + "device_password=?, accessories=?, physical_condition=?, reported_issue=?, diagnosis=?, "
                + "quoted_amount=?, technician_id=?, promised_date=?, warranty_days=?, internal_notes=? WHERE id=?";
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getDeviceType());
            ps.setString(2, t.getBrand());
            ps.setString(3, t.getModel());
            ps.setString(4, t.getSerialOrImei());
            ps.setString(5, t.getDevicePassword());
            ps.setString(6, t.getAccessories());
            ps.setString(7, t.getPhysicalCondition());
            ps.setString(8, t.getReportedIssue());
            ps.setString(9, t.getDiagnosis());
            ps.setBigDecimal(10, nz(t.getQuotedAmount()));
            ps.setObject(11, t.getTechnicianId());
            ps.setDate(12, t.getPromisedDate() == null ? null : java.sql.Date.valueOf(t.getPromisedDate()));
            ps.setInt(13, t.getWarrantyDays());
            ps.setString(14, t.getInternalNotes());
            ps.setInt(15, t.getId());
            ps.executeUpdate();
        }
    }

    public void updateStatus(int id, String status, LocalDateTime completedAt) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE repair_tickets SET status=?, completed_at=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setTimestamp(2, completedAt == null ? null : Timestamp.valueOf(completedAt));
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /** Met à jour le diagnostic et les notes internes du dossier. */
    public void updateDiagnosis(int id, String diagnosis, String internalNotes) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE repair_tickets SET diagnosis=?, internal_notes=? WHERE id=?")) {
            ps.setString(1, diagnosis);
            ps.setString(2, internalNotes);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /** Met à jour les montants calculés (main d'œuvre, pièces, total, soldes). */
    public void updateTotals(int id, BigDecimal laborCost, BigDecimal partsTotal, BigDecimal discount,
                             BigDecimal total, BigDecimal deposit, BigDecimal paid, BigDecimal due)
            throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE repair_tickets SET labor_cost=?, parts_total=?, discount=?, total=?, "
                             + "deposit_amount=?, paid_amount=?, due_amount=? WHERE id=?")) {
            ps.setBigDecimal(1, nz(laborCost));
            ps.setBigDecimal(2, nz(partsTotal));
            ps.setBigDecimal(3, nz(discount));
            ps.setBigDecimal(4, nz(total));
            ps.setBigDecimal(5, nz(deposit));
            ps.setBigDecimal(6, nz(paid));
            ps.setBigDecimal(7, nz(due));
            ps.setInt(8, id);
            ps.executeUpdate();
        }
    }

    public Optional<RepairTicket> byId(int id) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE r.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<RepairTicket> search(Filter f, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, f);
        sql.append("ORDER BY r.received_at DESC, r.id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairTicket> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Tous les dossiers récents (vue Kanban). */
    public List<RepairTicket> board(int limit) throws SQLException {
        return search(null, limit, 0);
    }

    public long count(Filter f) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM repair_tickets r "
                + "LEFT JOIN customers c ON c.id = r.customer_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, f);
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public List<RepairTicket> byCustomer(int customerId) throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     SELECT + "WHERE r.customer_id = ? ORDER BY r.received_at DESC, r.id DESC")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairTicket> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Compteur par statut (colonnes du Kanban). */
    public Map<String, Long> countsByStatus() throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : RepairStatus.BOARD) {
            counts.put(s, 0L);
        }
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, COUNT(*) AS n FROM repair_tickets GROUP BY status");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("status"), rs.getLong("n"));
            }
        }
        return counts;
    }

    /** Dossiers prêts à être retirés (alerte cloche / tableau de bord). */
    public List<RepairTicket> readyForPickup() throws SQLException {
        return search(new Filter(null, RepairStatus.PRETE, null, null, null, null), 50, 0);
    }

    /** Dossiers en retard (date promise dépassée, encore actifs). */
    public List<RepairTicket> late(LocalDate today) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT)
                .append("WHERE r.promised_date IS NOT NULL AND r.promised_date < ? ")
                .append("AND r.status NOT IN ('")
                .append(RepairStatus.LIVREE).append("', '").append(RepairStatus.ANNULEE).append("') ")
                .append("ORDER BY r.promised_date ASC LIMIT 50");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setDate(1, java.sql.Date.valueOf(today));
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairTicket> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** Nombre de dossiers en cours (ni livrés ni annulés). */
    public long countActive() throws SQLException {
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM repair_tickets WHERE status NOT IN ('LIVREE', 'ANNULEE')");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Dossiers d'un client (utilisé par la fiche client). */
    public List<RepairTicket> byCustomer(int customerId, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE r.customer_id = ? ")
                .append("ORDER BY r.received_at DESC, r.id DESC LIMIT ?");
        try (Connection c = DatabaseManager.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setInt(1, customerId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<RepairTicket> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    private void appendFilters(StringBuilder sql, List<Object> params, Filter f) {
        if (f == null) {
            return;
        }
        if (f.query() != null && !f.query().isBlank()) {
            sql.append("AND (r.number LIKE ? OR c.full_name LIKE ? OR r.serial_or_imei LIKE ? "
                    + "OR r.model LIKE ? OR c.phone LIKE ?) ");
            String like = "%" + f.query().trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        if (f.status() != null && !f.status().isBlank()) {
            sql.append("AND r.status = ? ");
            params.add(f.status());
        }
        if (f.technicianId() != null) {
            sql.append("AND r.technician_id = ? ");
            params.add(f.technicianId());
        }
        if (f.from() != null) {
            sql.append("AND r.received_at >= ? ");
            params.add(Timestamp.valueOf(f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            sql.append("AND r.received_at < ? ");
            params.add(Timestamp.valueOf(f.to().plusDays(1).atStartOfDay()));
        }
        if (Boolean.TRUE.equals(f.late())) {
            sql.append("AND r.promised_date IS NOT NULL AND r.promised_date < CURDATE() ")
                    .append("AND r.status NOT IN ('").append(RepairStatus.LIVREE).append("', '")
                    .append(RepairStatus.ANNULEE).append("') ");
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static RepairTicket map(ResultSet rs) throws SQLException {
        RepairTicket t = new RepairTicket();
        t.setId(rs.getInt("id"));
        t.setNumber(rs.getString("number"));
        t.setCustomerId(rs.getInt("customer_id"));
        t.setCustomerName(rs.getString("customer_name"));
        t.setCustomerPhone(rs.getString("customer_phone"));
        t.setDeviceType(rs.getString("device_type"));
        t.setBrand(rs.getString("brand"));
        t.setModel(rs.getString("model"));
        t.setSerialOrImei(rs.getString("serial_or_imei"));
        t.setDevicePassword(rs.getString("device_password"));
        t.setAccessories(rs.getString("accessories"));
        t.setPhysicalCondition(rs.getString("physical_condition"));
        t.setReportedIssue(rs.getString("reported_issue"));
        t.setDiagnosis(rs.getString("diagnosis"));
        t.setQuotedAmount(nz(rs.getBigDecimal("quoted_amount")));
        t.setStatus(rs.getString("status"));
        Object tech = rs.getObject("technician_id");
        t.setTechnicianId(tech == null ? null : ((Number) tech).intValue());
        t.setTechnicianName(rs.getString("technician_name"));
        Timestamp received = rs.getTimestamp("received_at");
        t.setReceivedAt(received == null ? null : received.toLocalDateTime());
        java.sql.Date promised = rs.getDate("promised_date");
        t.setPromisedDate(promised == null ? null : promised.toLocalDate());
        Timestamp completed = rs.getTimestamp("completed_at");
        t.setCompletedAt(completed == null ? null : completed.toLocalDateTime());
        t.setLaborCost(nz(rs.getBigDecimal("labor_cost")));
        t.setPartsTotal(nz(rs.getBigDecimal("parts_total")));
        t.setDiscount(nz(rs.getBigDecimal("discount")));
        t.setTotal(nz(rs.getBigDecimal("total")));
        t.setDepositAmount(nz(rs.getBigDecimal("deposit_amount")));
        t.setPaidAmount(nz(rs.getBigDecimal("paid_amount")));
        t.setDueAmount(nz(rs.getBigDecimal("due_amount")));
        t.setWarrantyDays(rs.getInt("warranty_days"));
        t.setInternalNotes(rs.getString("internal_notes"));
        Timestamp created = rs.getTimestamp("created_at");
        t.setCreatedAt(created == null ? null : created.toLocalDateTime());
        return t;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
