package com.techshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Encaissement d'une vente (sale_payments). Une vente peut porter plusieurs
 * lignes de paiement (paiement mixte espèces + carte, par exemple).
 */
public class SalePayment {

    private int id;
    private int saleId;
    private LocalDateTime paymentDate;
    private String method = PaymentMethod.ESPECES.name();
    private BigDecimal amount = BigDecimal.ZERO;
    private Integer cashSessionId;
    private Integer userId;
    private String note;

    public SalePayment() {
    }

    public SalePayment(String method, BigDecimal amount) {
        this.method = method;
        this.amount = amount;
    }

    public String methodLabel() {
        return PaymentMethod.libelle(method);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public BigDecimal getAmount() { return amount == null ? BigDecimal.ZERO : amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getCashSessionId() { return cashSessionId; }
    public void setCashSessionId(Integer cashSessionId) { this.cashSessionId = cashSessionId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
