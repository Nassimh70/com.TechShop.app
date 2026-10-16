package com.techshop.model;

import java.math.BigDecimal;

public class GoodsReceiptItem {
    private int id;
    private int receiptId;
    private int productId;
    private Integer productUnitId;
    private String imei;
    private String productRef;
    private String productName;
    private int quantity;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    public GoodsReceiptItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getReceiptId() { return receiptId; }
    public void setReceiptId(int receiptId) { this.receiptId = receiptId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public Integer getProductUnitId() { return productUnitId; }
    public void setProductUnitId(Integer productUnitId) { this.productUnitId = productUnitId; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getProductRef() { return productRef; }
    public void setProductRef(String productRef) { this.productRef = productRef; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost == null ? BigDecimal.ZERO : unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getTotal() { return total == null ? BigDecimal.ZERO : total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
