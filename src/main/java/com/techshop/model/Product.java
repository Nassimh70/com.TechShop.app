package com.techshop.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Produit du catalogue (SPEC §4.5, §5). Objet de transfert mutable : la couche
 * DAO le remplit depuis la base et l'interface y range la saisie du formulaire.
 * La quantité est le cache du stock disponible, tenu par le StockService.
 */
public class Product {

    private int id;
    private String ref;
    private String barcode;
    private String name;
    private String description;
    private Integer categoryId;
    private String categoryName;
    private Integer brandId;
    private String brandName;
    private String unit = "piece";
    private BigDecimal purchasePrice = BigDecimal.ZERO;
    private BigDecimal avgCost = BigDecimal.ZERO;
    private BigDecimal salePrice = BigDecimal.ZERO;
    private BigDecimal vatRate = new BigDecimal("19.00");
    private int alertThreshold = 5;
    private int quantity;
    private boolean trackImei;
    private int warrantyMonths;
    private String imagePath;
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Product() {
    }

    public Product(int id, String ref) {
        this.id = id;
        this.ref = ref;
    }

    // ------------------------------------------------------------------ accès

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getBrandId() {
        return brandId;
    }

    public void setBrandId(Integer brandId) {
        this.brandId = brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public int getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(int alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isTrackImei() {
        return trackImei;
    }

    public void setTrackImei(boolean trackImei) {
        this.trackImei = trackImei;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ------------------------------------------------------------- calculs

    /** Marge unitaire en valeur (prix de vente - prix d'achat). */
    public BigDecimal margin() {
        BigDecimal achat = purchasePrice == null ? BigDecimal.ZERO : purchasePrice;
        return salePrice.subtract(achat);
    }

    /** Taux de marge en pourcentage du prix d'achat. */
    public BigDecimal marginRate() {
        if (purchasePrice == null || purchasePrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return salePrice.subtract(purchasePrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(purchasePrice, 1, RoundingMode.HALF_UP);
    }

    /** Statut du stock (rupture / faible / normal). */
    public StockStatus stockStatus() {
        if (quantity <= 0) {
            return StockStatus.OUT;
        }
        return quantity <= alertThreshold ? StockStatus.LOW : StockStatus.OK;
    }

    @Override
    public String toString() {
        return name == null ? ref : name;
    }
}