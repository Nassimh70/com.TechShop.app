package com.techshop.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ligne d'achat / de vente (sale_items) : un produit, une quantité, un prix
 * unitaire figé, une remise de ligne et, pour les produits suivis, l'IMEI
 * précis vendu (product_unit_id).
 */
public class SaleItem {

    private int id;
    private int saleId;
    private int productId;
    private String productName;
    private String productRef;
    private Integer productUnitId;
    private String imei;
    private String description;
    private int quantity = 1;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal vatRate = new BigDecimal("19.00");
    private BigDecimal total = BigDecimal.ZERO;
    private int warrantyMonths;
    private int returnedQuantity;

    public SaleItem() {
    }

    /** Total HT de la ligne avant remise : prix × quantité. */
    public BigDecimal gross() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Recalcule le total de la ligne (brut − remise, jamais négatif). */
    public BigDecimal computeTotal() {
        BigDecimal t = gross().subtract(discount == null ? BigDecimal.ZERO : discount);
        if (t.signum() < 0) {
            t = BigDecimal.ZERO;
        }
        total = t.setScale(2, RoundingMode.HALF_UP);
        return total;
    }

    /** Quantité encore remboursable (vendue − déjà retournée). */
    public int returnableQuantity() {
        return Math.max(0, quantity - returnedQuantity);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductRef() { return productRef; }
    public void setProductRef(String productRef) { this.productRef = productRef; }
    public Integer getProductUnitId() { return productUnitId; }
    public void setProductUnitId(Integer productUnitId) { this.productUnitId = productUnitId; }
    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice == null ? BigDecimal.ZERO : unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getUnitCost() { return unitCost == null ? BigDecimal.ZERO : unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getDiscount() { return discount == null ? BigDecimal.ZERO : discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getVatRate() { return vatRate == null ? BigDecimal.ZERO : vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }
    public BigDecimal getTotal() { return total == null ? BigDecimal.ZERO : total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public int getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(int returnedQuantity) { this.returnedQuantity = returnedQuantity; }
}
