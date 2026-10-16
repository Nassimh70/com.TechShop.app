package com.techshop.model;

import java.math.BigDecimal;

public class PurchaseOrderItem {
    private int id;
    private int orderId;
    private int productId;
    private String productRef;
    private String productName;
    private int quantity;
    private int quantityReceived;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    public PurchaseOrderItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getProductRef() { return productRef; }
    public void setProductRef(String productRef) { this.productRef = productRef; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(int quantityReceived) { this.quantityReceived = quantityReceived; }
    public BigDecimal getUnitCost() { return unitCost == null ? BigDecimal.ZERO : unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getDiscount() { return discount == null ? BigDecimal.ZERO : discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTotal() { return total == null ? BigDecimal.ZERO : total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public int getQuantityRemaining() { return Math.max(0, quantity - quantityReceived); }
}
