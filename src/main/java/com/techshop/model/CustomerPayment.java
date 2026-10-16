package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerPayment {
    public enum Method { ESPECES, CARTE, VIREMENT }

    private int id;
    private int customerId;
    private String customerName;
    private BigDecimal amount = BigDecimal.ZERO;
    private Method paymentMethod = Method.ESPECES;
    private LocalDateTime paymentDate;
    private Integer cashSessionId;
    private String note;
    private int userId;
    private String username;

    public CustomerPayment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
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
