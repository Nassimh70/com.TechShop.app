package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mouvement de caisse (cash_movements) : entrée (encaissement, apport) ou
 * sortie (dépense, prélèvement) rattachée à une session. Peut référencer
 * l'opération à l'origine du mouvement (SALE, CUSTOMER_PAYMENT, SUPPLIER_PAYMENT...).
 */
public class CashMovement {

    private long id;
    private int cashSessionId;
    private String type = CashMovementType.ENTREE.name();
    private BigDecimal amount = BigDecimal.ZERO;
    private String reason;
    private Integer categoryId;
    private String categoryName;
    private String referenceType;
    private Integer referenceId;
    private int userId;
    private String username;
    private LocalDateTime movementDate;
    private String note;

    public CashMovement() {
    }

    public String typeLabel() {
        return CashMovementType.valueOf(type).getLabel();
    }

    /** Montant signé pour l'affichage (+ entrée, − sortie). */
    public BigDecimal signedAmount() {
        BigDecimal a = getAmount();
        return CashMovementType.SORTIE.name().equals(type) ? a.negate() : a;
    }

    public boolean isEntree() {
        return CashMovementType.ENTREE.name().equals(type);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getCashSessionId() { return cashSessionId; }
    public void setCashSessionId(int cashSessionId) { this.cashSessionId = cashSessionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount == null ? BigDecimal.ZERO : amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Integer getReferenceId() { return referenceId; }
    public void setReferenceId(Integer referenceId) { this.referenceId = referenceId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDateTime movementDate) { this.movementDate = movementDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
