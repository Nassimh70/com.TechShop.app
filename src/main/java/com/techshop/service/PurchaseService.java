package com.techshop.service;

import com.techshop.config.Tx;
import com.techshop.dao.*;
import com.techshop.model.*;
import com.techshop.util.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchaseService {
    private static final PurchaseService INSTANCE = new PurchaseService();

    private final PurchaseOrderDao orders = new PurchaseOrderDao();
    private final GoodsReceiptDao receipts = new GoodsReceiptDao();
    private final SupplierDao suppliers = new SupplierDao();
    private final ProductDao products = new ProductDao();
    private final ProductUnitDao units = new ProductUnitDao();
    private final StockMovementDao movements = new StockMovementDao();
    private final ActivityLogService journal = ActivityLogService.get();
    private final SessionContext session = SessionContext.get();
    private final StockService stock = StockService.get();

    private PurchaseService() {}

    public static PurchaseService get() { return INSTANCE; }

    private int userId() {
        var u = session.getUser();
        return u == null ? 1 : u.getId();
    }

    // Reception result
    public static class ReceptionResult {
        public int receiptId;
        public int unitsCreated;
        public ReceptionResult(int id, int u) { receiptId = id; unitsCreated = u; }
    }

    public ReceptionResult receive(int orderId, List<ReceptionLine> lines, String notes) throws SQLException {
        PermissionService.get().check(PermissionDefaults.M_STOCK, PermissionService.Action.CREATE);
        if (lines == null || lines.isEmpty()) throw new ValidationException("Lignes de réception vides");
        return Tx.run(() -> doReceive(orderId, lines, notes));
    }

    public static class ReceptionLine {
        public int productId;
        public int quantity;
        public BigDecimal unitCost;
        public List<String> imeis; // for tracked products
        public ReceptionLine(int pid, int q, BigDecimal cost, List<String> imeis) {
            productId = pid; quantity = q; unitCost = cost == null ? BigDecimal.ZERO : cost; this.imeis = imeis == null ? List.of() : imeis;
        }
    }

    private ReceptionResult doReceive(int orderId, List<ReceptionLine> lines, String notes) throws SQLException {
        PurchaseOrder po = orders.byId(orderId).orElseThrow(() -> new ValidationException("Bon de commande introuvable"));
        // Prépare les lignes de la réception (quantité reçue réelle, IMEI éventuels)
        List<GoodsReceiptItem> items = new ArrayList<>();
        for (ReceptionLine rl : lines) {
            if (rl.quantity <= 0) continue;
            Product p = products.findById(rl.productId).orElseThrow(() -> new ValidationException("Produit introuvable"));
            GoodsReceiptItem it = new GoodsReceiptItem();
            it.setProductId(p.getId());
            it.setProductRef(p.getRef());
            it.setProductName(p.getName());
            it.setQuantity(rl.quantity);
            it.setUnitCost(rl.unitCost);
            it.setTotal(rl.unitCost.multiply(BigDecimal.valueOf(rl.quantity)));
            items.add(it);
        }
        if (items.isEmpty()) throw new ValidationException("Aucune quantité reçue.");
        GoodsReceipt receipt = buildReceipt(po, notes);
        BigDecimal totalRec = items.stream().map(GoodsReceiptItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        receipt.setTotal(totalRec);
        int receiptId = receipts.insert(receipt, items);
        int unitsCreated = 0;
        for (ReceptionLine rl : lines) {
            Product p = products.lockForUpdate(rl.productId).orElseThrow(() -> new ValidationException("Produit introuvable"));
            BigDecimal cost = rl.unitCost;
            int qty = rl.quantity;
            if (qty <= 0) continue;
            if (p.isTrackImei()) {
                // create units from IMEIs
                int created = 0;
                for (String imei : rl.imeis) {
                    if (imei == null || imei.isBlank()) continue;
                    units.insert(p.getId(), imei.trim(), null, ProductUnit.EN_STOCK,
                            cost, po.getSupplierId(), java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
                    created++;
                }
                unitsCreated += created;
                // stock entry for tracked count
                stock.apply(p.getId(), StockMovementType.ENTREE, created, null, cost, "Réception BC " + po.getNumber(), "GOODS_RECEIPT", receiptId);
                // update avg cost with created qty at cost
                updateWeightedAverageCost(p, created, cost);
            } else {
                stock.apply(p.getId(), StockMovementType.ENTREE, qty, null, cost, "Réception BC " + po.getNumber(), "GOODS_RECEIPT", receiptId);
                updateWeightedAverageCost(p, qty, cost);
            }
            orders.incrementReceived(po.getId(), p.getId(), qty);
        }
        // update status
        boolean allReceived = true;
        for (PurchaseOrderItem it : orders.byId(po.getId()).orElse(po).getItems()) {
            if (it.getQuantityReceived() < it.getQuantity()) { allReceived = false; break; }
        }
        orders.updateStatus(po.getId(), allReceived ? PurchaseOrder.Status.RECU : PurchaseOrder.Status.RECU_PARTIEL);
        // supplier balance increase by total of receipt
        suppliers.updateBalance(po.getSupplierId(), totalRec);
        journal.log(userId(), "RECEPTION", "purchase_orders", po.getId(), "Réception BC " + po.getNumber());
        return new ReceptionResult(receiptId, unitsCreated);
    }

    private GoodsReceipt buildReceipt(PurchaseOrder po, String notes) {
        GoodsReceipt gr = new GoodsReceipt();
        gr.setOrderId(po.getId());
        gr.setSupplierId(po.getSupplierId());
        gr.setReceiptDate(LocalDate.now());
        gr.setTotal(BigDecimal.ZERO);
        gr.setNotes(notes);
        gr.setUserId(userId());
        return gr;
    }

    // Weighted average cost: newAvg = ((oldQty*oldAvg) + (qty*cost)) / (oldQty+qty)
    private void updateWeightedAverageCost(Product p, int qty, BigDecimal cost) throws SQLException {
        if (qty <= 0) return;
        int oldQty = p.getQuantity();
        BigDecimal oldAvg = p.getAvgCost() == null ? BigDecimal.ZERO : p.getAvgCost();
        BigDecimal oldVal = oldAvg.multiply(BigDecimal.valueOf(oldQty));
        BigDecimal newVal = cost.multiply(BigDecimal.valueOf(qty));
        int newQty = oldQty + qty;
        BigDecimal newAvg = BigDecimal.ZERO;
        if (newQty > 0) {
            newAvg = oldVal.add(newVal).divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP);
        }
        p.setAvgCost(newAvg);
        p.setQuantity(newQty);
        products.update(p);
    }
}
