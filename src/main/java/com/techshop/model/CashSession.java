package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Session de caisse (SPEC §4.10). Ouverte avec un fonds initial, clôturée par un
 * comptage par coupures ; l'écart (compté − attendu) est alors calculé et
 * conservé. Une vente ne peut être enregistrée que dans une session ouverte.
 */
public class CashSession {

    public static final String OUVERTE = "OUVERTE";
    public static final String FERMEE = "FERMEE";

    private int id;
    private String number;
    private int userId;
    private String username;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private BigDecimal openingFloat = BigDecimal.ZERO;
    private BigDecimal expectedCash = BigDecimal.ZERO;
    private BigDecimal countedCash;
    private BigDecimal difference;
    private BigDecimal expectedCard = BigDecimal.ZERO;
    private BigDecimal expectedTransfer = BigDecimal.ZERO;
    private BigDecimal totalSales = BigDecimal.ZERO;
    private String status = OUVERTE;
    private String note;
    // Agrégats de lecture (mouvements)
    private BigDecimal cashIn = BigDecimal.ZERO;
    private BigDecimal cashOut = BigDecimal.ZERO;
    private int salesCount;

    public boolean isOpen() {
        return OUVERTE.equals(status);
    }

    public String statusLabel() {
        return isOpen() ? "Ouverte" : "Fermée";
    }

    public String statusCss() {
        return isOpen() ? "badge-success" : "badge-neutral";
    }

    /** Écart lisible et coloré selon le signe (coloration gérée par la vue). */
    public String differenceLabel() {
        if (difference == null) {
            return "—";
        }
        return com.techshop.util.Format.money(difference);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getOpeningFloat() { return openingFloat == null ? BigDecimal.ZERO : openingFloat; }
    public void setOpeningFloat(BigDecimal openingFloat) { this.openingFloat = openingFloat; }
    public BigDecimal getExpectedCash() { return expectedCash == null ? BigDecimal.ZERO : expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }
    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
    public BigDecimal getExpectedCard() { return expectedCard == null ? BigDecimal.ZERO : expectedCard; }
    public void setExpectedCard(BigDecimal expectedCard) { this.expectedCard = expectedCard; }
    public BigDecimal getExpectedTransfer() {
        return expectedTransfer == null ? BigDecimal.ZERO : expectedTransfer;
    }
    public void setExpectedTransfer(BigDecimal expectedTransfer) { this.expectedTransfer = expectedTransfer; }
    public BigDecimal getTotalSales() { return totalSales == null ? BigDecimal.ZERO : totalSales; }
    public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public BigDecimal getCashIn() { return cashIn == null ? BigDecimal.ZERO : cashIn; }
    public void setCashIn(BigDecimal cashIn) { this.cashIn = cashIn; }
    public BigDecimal getCashOut() { return cashOut == null ? BigDecimal.ZERO : cashOut; }
    public void setCashOut(BigDecimal cashOut) { this.cashOut = cashOut; }
    public int getSalesCount() { return salesCount; }
    public void setSalesCount(int salesCount) { this.salesCount = salesCount; }
}
