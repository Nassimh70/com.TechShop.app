package com.techshop.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public class Supplier {
    private int id;
    private String name;
    private String contactName;
    private String phone;
    private String email;
    private String address;
    private BigDecimal balance = BigDecimal.ZERO;
    private String notes;
    private boolean active = true;
    private LocalDateTime createdAt;

    public Supplier() {}

    public Supplier(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
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
