package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SupplierPayment {
    public enum Method { ESPECES, VIREMENT, CHEQUE }

    private int id;
    private String number;
    private int supplierId;
    private String supplierName;
    private BigDecimal amount = BigDecimal.ZERO;
    private Method paymentMethod = Method.ESPECES;
    private LocalDateTime paymentDate;
    private Integer cashSessionId;
    private String note;
    private int userId;
    private String username;

    public SupplierPayment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public BigDecimal getAmount() { return amount == null ? BigDecimal.ZERO : amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Method getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(Method paymentMethod) { this.paymentMethod = paymentMethod; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public Integer getCashSessionId() { return cashSessionId; }
    public void setCashSessionId(Integer cashSessionId) { this.cashSessionId = cashSessionId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
