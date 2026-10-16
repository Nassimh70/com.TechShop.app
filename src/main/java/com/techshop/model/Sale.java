package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vente (SPEC §4.3, §4.4). Objet agrégé : en-tête (sales) + lignes (sale_items)
 * + règlements (sale_payments). Tous les montants sont en dinar algérien.
 * Les statuts possibles : PAYEE, PARTIELLE, IMPAYEE, REMBOURSEE (payment_status)
 * et VALIDEE, ANNULEE (status).
 */
public class Sale {

    // ---------------------------------------------------- statuts de règlement
    public static final String PAYEE = "PAYEE";
    public static final String PARTIELLE = "PARTIELLE";
    public static final String IMPAYEE = "IMPAYEE";
    public static final String REMBOURSEE = "REMBOURSEE";
    // ------------------------------------------------------------ statuts vente
    public static final String VALIDEE = "VALIDEE";
    public static final String ANNULEE = "ANNULEE";

    private int id;
    private String number;
    private LocalDateTime saleDate;
    private Integer customerId;
    private String customerName;
    private int userId;
    private String username;
    private Integer cashSessionId;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal dueAmount = BigDecimal.ZERO;
    private BigDecimal profit = BigDecimal.ZERO;
    private String paymentStatus = PAYEE;
    private String status = VALIDEE;
    private String note;
    private LocalDateTime createdAt;

    // Champs de lecture (jointures, agrégats)
    private int itemCount;
    private String methods;
    private int returnedQuantity;

    private List<SaleItem> items = new ArrayList<>();
    private List<SalePayment> payments = new ArrayList<>();

    public Sale() {
    }

    /** Solde restant dû (jamais négatif). */
    public BigDecimal restant() {
        BigDecimal reste = getTotal().subtract(getPaidAmount());
        return reste.signum() < 0 ? BigDecimal.ZERO : reste;
    }

    /** Libellé français du statut de règlement. */
    public String paymentStatusLabel() {
        return switch (paymentStatus == null ? "" : paymentStatus) {
            case PAYEE -> "Payée";
            case PARTIELLE -> "Partielle";
            case IMPAYEE -> "Impayée";
            case REMBOURSEE -> "Remboursée";
            default -> paymentStatus == null ? "—" : paymentStatus;
        };
    }

    public String statusLabel() {
        return ANNULEE.equals(status) ? "Annulée" : "Validée";
    }

    /** Classe CSS du badge de statut de règlement. */
    public String paymentStatusCss() {
        return switch (paymentStatus == null ? "" : paymentStatus) {
            case PAYEE -> "badge-success";
            case PARTIELLE -> "badge-warning";
            case IMPAYEE -> "badge-danger";
            case REMBOURSEE -> "badge-neutral";
            default -> "badge-neutral";
        };
    }

    /** Modes de paiement lisibles (colonne « Paiement » du tableau). */
    public String methodsLabel() {
        if (methods == null || methods.isBlank()) {
            return getDueAmount().signum() > 0 ? "Crédit" : "—";
        }
        String[] parts = methods.split(",");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(PaymentMethod.libelle(p.trim()));
        }
        if (getDueAmount().signum() > 0) {
            sb.append(" + Crédit");
        }
        return sb.toString();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public LocalDateTime getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDateTime saleDate) { this.saleDate = saleDate; }
    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getCashSessionId() { return cashSessionId; }
    public void setCashSessionId(Integer cashSessionId) { this.cashSessionId = cashSessionId; }
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
    public BigDecimal getDueAmount() { return dueAmount == null ? BigDecimal.ZERO : dueAmount; }
    public void setDueAmount(BigDecimal dueAmount) { this.dueAmount = dueAmount; }
    public BigDecimal getProfit() { return profit == null ? BigDecimal.ZERO : profit; }
    public void setProfit(BigDecimal profit) { this.profit = profit; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public String getMethods() { return methods; }
    public void setMethods(String methods) { this.methods = methods; }
    public int getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(int returnedQuantity) { this.returnedQuantity = returnedQuantity; }
    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items == null ? new ArrayList<>() : items; }
    public List<SalePayment> getPayments() { return payments; }
    public void setPayments(List<SalePayment> payments) {
        this.payments = payments == null ? new ArrayList<>() : payments;
    }
}
