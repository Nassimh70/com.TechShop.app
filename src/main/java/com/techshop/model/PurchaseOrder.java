package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrder {
    public enum Status { BROUILLON, ENVOYE, RECU_PARTIEL, RECU, ANNULE }

    private int id;
    private String number;
    private int supplierId;
    private String supplierName;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private Status status = Status.BROUILLON;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String notes;
    private int userId;
    private String username;
    private LocalDateTime createdAt;
    private final List<PurchaseOrderItem> items = new ArrayList<>();

    public PurchaseOrder() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public void setExpectedDate(LocalDate expectedDate) { this.expectedDate = expectedDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getStatusLabel() {
        return switch (status) {
            case BROUILLON -> "Brouillon";
            case ENVOYE -> "Envoyé";
            case RECU_PARTIEL -> "Reçu partiellement";
            case RECU -> "Reçu";
            case ANNULE -> "Annulé";
        };
    }
    public BigDecimal getSubtotal() { return subtotal == null ? BigDecimal.ZERO : subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscount() { return discount == null ? BigDecimal.ZERO : discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTax() { return tax == null ? BigDecimal.ZERO : tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public BigDecimal getTotal() { return total == null ? BigDecimal.ZERO : total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getPaidAmount() { return paidAmount == null ? BigDecimal.ZERO : paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getDueAmount() { return getTotal().subtract(getPaidAmount()); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<PurchaseOrderItem> getItems() { return items; }
}
