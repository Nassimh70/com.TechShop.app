package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Dossier de réparation (SPEC §4.9, §5). Objet agrégé : en-tête (repair_tickets)
 * + chronologie des statuts (repair_status_history) + pièces (repair_parts) +
 * paiements/acomptes (repair_payments) + pièces jointes (repair_attachments).
 * Tous les montants sont en dinar algérien.
 */
public class RepairTicket {

    private int id;
    private String number;
    private int customerId;
    private String customerName;
    private String customerPhone;
    private String deviceType;
    private String brand;
    private String model;
    private String serialOrImei;
    private String devicePassword;
    private String accessories;
    private String physicalCondition;
    private String reportedIssue;
    private String diagnosis;
    private BigDecimal quotedAmount = BigDecimal.ZERO;
    private String status = RepairStatus.RECU;
    private Integer technicianId;
    private String technicianName;
    private LocalDateTime receivedAt;
    private LocalDate promisedDate;
    private LocalDateTime completedAt;
    private BigDecimal laborCost = BigDecimal.ZERO;
    private BigDecimal partsTotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal depositAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal dueAmount = BigDecimal.ZERO;
    private int warrantyDays;
    private String internalNotes;
    private LocalDateTime createdAt;

    private final List<RepairStatusHistory> history = new ArrayList<>();
    private final List<RepairPart> parts = new ArrayList<>();
    private final List<RepairPayment> payments = new ArrayList<>();
    private final List<RepairAttachment> attachments = new ArrayList<>();

    // ------------------------------------------------------------- présentation

    /** « Marque Modèle » ou le type d'appareil, jamais vide. */
    public String deviceLabel() {
        String m = ((brand == null ? "" : brand) + " " + (model == null ? "" : model)).trim();
        if (!m.isBlank()) {
            return m;
        }
        return deviceType == null || deviceType.isBlank() ? "Appareil" : deviceType;
    }

    public String customerLabel() {
        return customerName == null || customerName.isBlank() ? "Client #" + customerId : customerName;
    }

    public String statusLabel() {
        return RepairStatus.label(status);
    }

    public boolean isTerminal() {
        return RepairStatus.isTerminal(status);
    }

    /** En retard : date promise dépassée et dossier encore actif. */
    public boolean isLate() {
        return isLate(LocalDate.now());
    }

    public boolean isLate(LocalDate today) {
        return promisedDate != null && today != null && promisedDate.isBefore(today)
                && RepairStatus.isActive(status);
    }

    /** Nombre de jours de retard (0 si aucun). */
    public long lateDays() {
        return lateDays(LocalDate.now());
    }

    public long lateDays(LocalDate today) {
        if (!isLate(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(promisedDate, today);
    }

    /** Fin de garantie (livraison + jours de garantie), null si non livré. */
    public LocalDate warrantyEnd() {
        if (completedAt == null || warrantyDays <= 0) {
            return null;
        }
        return completedAt.toLocalDate().plusDays(warrantyDays);
    }

    // ------------------------------------------------------------------- accès

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialOrImei() {
        return serialOrImei;
    }

    public void setSerialOrImei(String serialOrImei) {
        this.serialOrImei = serialOrImei;
    }

    public String getDevicePassword() {
        return devicePassword;
    }

    public void setDevicePassword(String devicePassword) {
        this.devicePassword = devicePassword;
    }

    public String getAccessories() {
        return accessories;
    }

    public void setAccessories(String accessories) {
        this.accessories = accessories;
    }

    public String getPhysicalCondition() {
        return physicalCondition;
    }

    public void setPhysicalCondition(String physicalCondition) {
        this.physicalCondition = physicalCondition;
    }

    public String getReportedIssue() {
        return reportedIssue;
    }

    public void setReportedIssue(String reportedIssue) {
        this.reportedIssue = reportedIssue;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public BigDecimal getQuotedAmount() {
        return quotedAmount;
    }

    public void setQuotedAmount(BigDecimal quotedAmount) {
        this.quotedAmount = quotedAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Integer technicianId) {
        this.technicianId = technicianId;
    }

    public String getTechnicianName() {
        return technicianName;
    }

    public void setTechnicianName(String technicianName) {
        this.technicianName = technicianName;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDate getPromisedDate() {
        return promisedDate;
    }

    public void setPromisedDate(LocalDate promisedDate) {
        this.promisedDate = promisedDate;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public void setLaborCost(BigDecimal laborCost) {
        this.laborCost = laborCost;
    }

    public BigDecimal getPartsTotal() {
        return partsTotal;
    }

    public void setPartsTotal(BigDecimal partsTotal) {
        this.partsTotal = partsTotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public BigDecimal getDueAmount() {
        return dueAmount;
    }

    public void setDueAmount(BigDecimal dueAmount) {
        this.dueAmount = dueAmount;
    }

    public int getWarrantyDays() {
        return warrantyDays;
    }

    public void setWarrantyDays(int warrantyDays) {
        this.warrantyDays = warrantyDays;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public void setInternalNotes(String internalNotes) {
        this.internalNotes = internalNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<RepairStatusHistory> getHistory() {
        return history;
    }

    public List<RepairPart> getParts() {
        return parts;
    }

    public List<RepairPayment> getPayments() {
        return payments;
    }

    public List<RepairAttachment> getAttachments() {
        return attachments;
    }
}
