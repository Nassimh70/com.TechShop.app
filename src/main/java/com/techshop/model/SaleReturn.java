package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Retour / remboursement d'une vente (sale_returns + sale_return_items).
 * Peut être partiel (quelques lignes, quelques quantités) ou total.
 */
public class SaleReturn {

    private int id;
    private String number;
    private int saleId;
    private LocalDateTime returnDate;
    private BigDecimal total = BigDecimal.ZERO;
    private String reason;
    private String refundMethod = PaymentMethod.ESPECES.name();
    private Integer cashSessionId;
    private int userId;
    private String username;
    private List<SaleReturnItem> items = new ArrayList<>();

    public SaleReturn() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }
    public BigDecimal getTotal() { return total == null ? BigDecimal.ZERO : total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
    public Integer getCashSessionId() { return cashSessionId; }
    public void setCashSessionId(Integer cashSessionId) { this.cashSessionId = cashSessionId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public List<SaleReturnItem> getItems() { return items; }
    public void setItems(List<SaleReturnItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    /** Ligne d'un retour : quantité rendue d'une ligne de vente. */
    public static class SaleReturnItem {
        private int id;
        private int returnId;
        private int saleItemId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getReturnId() { return returnId; }
        public void setReturnId(int returnId) { this.returnId = returnId; }
        public int getSaleItemId() { return saleItemId; }
        public void setSaleItemId(int saleItemId) { this.saleItemId = saleItemId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice == null ? BigDecimal.ZERO : unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getTotal() { return total == null ? BigDecimal.ZERO : total; }
        public void setTotal(BigDecimal total) { this.total = total; }
    }
}
