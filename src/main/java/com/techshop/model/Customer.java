package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Customer {
    private int id;
    private String code;
    private String fullName;
    private String phone;
    private String phone2;
    private String email;
    private String address;
    private BigDecimal balance = BigDecimal.ZERO;
    private String notes;
    private boolean active = true;
    private LocalDateTime createdAt;

    public Customer() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPhone2() { return phone2; }
    public void setPhone2(String phone2) { this.phone2 = phone2; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getBalance() { return balance == null ? BigDecimal.ZERO : balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
