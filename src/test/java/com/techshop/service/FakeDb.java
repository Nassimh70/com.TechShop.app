package com.techshop.service;

import com.techshop.dao.CashMovementDao;
import com.techshop.dao.CashSessionDao;
import com.techshop.dao.CustomerDao;
import com.techshop.dao.ProductDao;
import com.techshop.dao.ProductUnitDao;
import com.techshop.dao.SaleDao;
import com.techshop.dao.SaleItemDao;
import com.techshop.dao.SalePaymentDao;
import com.techshop.dao.SaleReturnDao;
import com.techshop.dao.StockMovementDao;
import com.techshop.model.CashMovement;
import com.techshop.model.CashSession;
import com.techshop.model.Product;
import com.techshop.model.ProductUnit;
import com.techshop.model.Sale;
import com.techshop.model.SaleItem;
import com.techshop.model.SalePayment;
import com.techshop.model.SaleReturn;
import com.techshop.model.StockMovement;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base de données en mémoire pour les tests des services Vente / Caisse
 * (SPEC §6.2) : chaque DAO est un double qui n'ouvre aucune connexion. On peut
 * ainsi vérifier toute la logique d'encaissement, de clôture et de retour sans
 * MySQL, {@link com.techshop.config.Tx} exécutant alors le bloc directement.
 */
final class FakeDb {

    final Map<Integer, Product> products = new LinkedHashMap<>();
    final List<StockMovement> movements = new ArrayList<>();
    final Map<Long, ProductUnit> units = new LinkedHashMap<>();
    final Map<Integer, BigDecimal> balances = new HashMap<>();
    final Map<Integer, Sale> sales = new LinkedHashMap<>();
    final int[] saleSeq = {0};
    final long[] saleNumber = {0};
    final List<SaleItem> saleItems = new ArrayList<>();
    final int[] itemSeq = {0};
    final List<SalePayment> salePayments = new ArrayList<>();
    final List<SaleReturn> saleReturns = new ArrayList<>();
    final Map<Integer, Integer> returnedByItem = new HashMap<>();
    final List<CashSession> sessions = new ArrayList<>();
    final int[] sessionSeq = {0};
    final long[] sessionNumber = {0};
    final List<CashMovement> cashMovements = new ArrayList<>();
    final int[] movementSeq = {0};
    final List<String> audit = new ArrayList<>();

    final ActivityAudit journal =
            (userId, action, entity, entityId, details) -> audit.add(action + ":" + entity);

    final ProductDao productDao = new FakeProductDao();
    final StockMovementDao movementDao = new FakeMovementDao();
    final ProductUnitDao unitDao = new FakeUnitDao();
    final SaleDao saleDao = new FakeSaleDao();
    final SaleItemDao itemDao = new FakeItemDao();
    final SalePaymentDao paymentDao = new FakePaymentDao();
    final SaleReturnDao returnDao = new FakeReturnDao();
    final CustomerDao customerDao = new FakeCustomerDao();
    final CashSessionDao cashSessionDao = new FakeCashSessionDao();
    final CashMovementDao cashMovementDao = new FakeCashMovementDao();

    StockService stockService() {
        return new StockService(productDao, movementDao, unitDao, journal);
    }

    CashService cashService() {
        return new CashService(cashSessionDao, cashMovementDao, paymentDao, saleDao, journal);
    }

    int quantityOf(int productId) {
        return products.get(productId).getQuantity();
    }

    // ------------------------------------------------------------- produits

    private final class FakeProductDao extends ProductDao {

        @Override
        public Optional<Product> findById(int id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public Optional<Product> lockForUpdate(int id) {
            Product p = products.get(id);
            if (p == null) {
                return Optional.empty();
            }
            Product copie = new Product(p.getId(), p.getRef());
            copie.setName(p.getName());
            copie.setQuantity(p.getQuantity());
            copie.setAvgCost(p.getAvgCost());
            copie.setPurchasePrice(p.getPurchasePrice());
            copie.setSalePrice(p.getSalePrice());
            copie.setVatRate(p.getVatRate());
            copie.setAlertThreshold(p.getAlertThreshold());
            copie.setBarcode(p.getBarcode());
            copie.setTrackImei(p.isTrackImei());
            return Optional.of(copie);
        }

        @Override
        public void setQuantity(int id, int quantity) {
            Product p = products.get(id);
            if (p != null) {
                p.setQuantity(quantity);
            }
        }
    }

    private final class FakeMovementDao extends StockMovementDao {

        @Override
        public long insert(int productId, Integer productUnitId, String type, int quantity,
                           int quantityBefore, int quantityAfter, BigDecimal unitCost, String reason,
                           String referenceType, Integer referenceId, Integer userId,
                           LocalDateTime movementDate) {
            long id = movements.size() + 1L;
            movements.add(new StockMovement(id, productId, null, null, productUnitId, null, type,
                    quantity, quantityBefore, quantityAfter, unitCost, reason, referenceType,
                    referenceId, userId, null, movementDate, null));
            return id;
        }
    }

    // --------------------------------------------------------------- unités

    private final class FakeUnitDao extends ProductUnitDao {

        @Override
        public Optional<ProductUnit> findById(long id) {
            return Optional.ofNullable(units.get(id));
        }

        @Override
        public void markSold(long id, int saleId, long saleItemId) {
            ProductUnit u = units.get(id);
            if (u != null) {
                units.put(id, new ProductUnit(u.id(), u.productId(), u.productName(), u.imei(),
                        u.serialNumber(), ProductUnit.VENDU, u.purchasePrice(), saleId,
                        u.receivedAt(), LocalDateTime.now()));
            }
        }

        @Override
        public void updateStatus(long id, String status, Integer saleId) {
            ProductUnit u = units.get(id);
            if (u != null) {
                units.put(id, new ProductUnit(u.id(), u.productId(), u.productName(), u.imei(),
                        u.serialNumber(), status, u.purchasePrice(), saleId, u.receivedAt(),
                        u.soldAt()));
            }
        }
    }

    // --------------------------------------------------------------- ventes

    private final class FakeSaleDao extends SaleDao {

        @Override
        public long nextNumber() {
            return ++saleNumber[0];
        }

        @Override
        public int insert(Sale s) {
            int id = ++saleSeq[0];
            s.setId(id);
            sales.put(id, s);
            return id;
        }

        @Override
        public Optional<Sale> byId(int id) {
            return Optional.ofNullable(sales.get(id));
        }

        @Override
        public void updateSettlement(int id, BigDecimal paid, BigDecimal due, String paymentStatus) {
            Sale s = sales.get(id);
            if (s != null) {
                s.setPaidAmount(paid);
                s.setDueAmount(due);
                s.setPaymentStatus(paymentStatus);
            }
        }

        @Override
        public void setStatus(int id, String status) {
            Sale s = sales.get(id);
            if (s != null) {
                s.setStatus(status);
            }
        }

        @Override
        public BigDecimal sumTotalBySession(int sessionId) {
            return sales.values().stream()
                    .filter(s -> s.getCashSessionId() != null && s.getCashSessionId() == sessionId)
                    .filter(s -> !Sale.ANNULEE.equals(s.getStatus()))
                    .map(Sale::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public long countBySession(int sessionId) {
            return sales.values().stream()
                    .filter(s -> s.getCashSessionId() != null && s.getCashSessionId() == sessionId)
                    .filter(s -> !Sale.ANNULEE.equals(s.getStatus()))
                    .count();
        }
    }

    private final class FakeItemDao extends SaleItemDao {

        @Override
        public long insert(SaleItem it) {
            long id = ++itemSeq[0];
            it.setId((int) id);
            saleItems.add(it);
            return id;
        }

        @Override
        public List<SaleItem> listBySale(int saleId) {
            List<SaleItem> list = new ArrayList<>();
            for (SaleItem it : saleItems) {
                if (it.getSaleId() == saleId) {
                    it.setReturnedQuantity(returnedByItem.getOrDefault(it.getId(), 0));
                    list.add(it);
                }
            }
            return list;
        }
    }

    private final class FakePaymentDao extends SalePaymentDao {

        @Override
        public long insert(SalePayment p) {
            long id = salePayments.size() + 1L;
            p.setId((int) id);
            salePayments.add(p);
            return id;
        }

        @Override
        public List<SalePayment> listBySale(int saleId) {
            return salePayments.stream().filter(p -> p.getSaleId() == saleId).toList();
        }

        @Override
        public BigDecimal sumBySessionAndMethod(int sessionId, String method) {
            return salePayments.stream()
                    .filter(p -> p.getCashSessionId() != null && p.getCashSessionId() == sessionId)
                    .filter(p -> method.equals(p.getMethod()))
                    .map(SalePayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private final class FakeReturnDao extends SaleReturnDao {

        @Override
        public long nextNumber() {
            return saleReturns.size() + 1L;
        }

        @Override
        public int insert(SaleReturn r) {
            int id = saleReturns.size() + 1;
            r.setId(id);
            if (r.getNumber() == null || r.getNumber().isBlank()) {
                r.setNumber("RT-" + String.format("%06d", nextNumber()));
            }
            saleReturns.add(r);
            return id;
        }

        @Override
        public long insertItem(int returnId, int saleItemId, int quantity, BigDecimal unitPrice,
                               BigDecimal total, String productName) {
            returnedByItem.merge(saleItemId, quantity, Integer::sum);
            return returnedByItem.size();
        }

        @Override
        public int returnedQuantity(int saleItemId) {
            return returnedByItem.getOrDefault(saleItemId, 0);
        }

        @Override
        public List<SaleReturn> listBySale(int saleId) {
            return saleReturns.stream().filter(r -> r.getSaleId() == saleId).toList();
        }
    }

    // --------------------------------------------------------------- clients

    private final class FakeCustomerDao extends CustomerDao {

        @Override
        public void updateBalance(int id, BigDecimal delta) throws SQLException {
            balances.merge(id, delta, BigDecimal::add);
        }
    }

    // ---------------------------------------------------------------- caisse

    private final class FakeCashSessionDao extends CashSessionDao {

        @Override
        public long nextNumber() {
            return ++sessionNumber[0];
        }

        @Override
        public int insert(CashSession s) {
            int id = ++sessionSeq[0];
            s.setId(id);
            if (s.getNumber() == null || s.getNumber().isBlank()) {
                s.setNumber("C-" + String.format("%06d", ++sessionNumber[0]));
            }
            sessions.add(s);
            return id;
        }

        @Override
        public Optional<CashSession> findOpenByUser(int userId) {
            return sessions.stream()
                    .filter(CashSession::isOpen)
                    .filter(s -> s.getUserId() == userId)
                    .findFirst();
        }

        @Override
        public Optional<CashSession> findAnyOpen() {
            return sessions.stream().filter(CashSession::isOpen).findFirst();
        }

        @Override
        public Optional<CashSession> byId(int id) {
            return sessions.stream().filter(s -> s.getId() == id).findFirst();
        }

        @Override
        public void close(int id, BigDecimal expectedCash, BigDecimal countedCash,
                          BigDecimal difference, BigDecimal expectedCard, BigDecimal expectedTransfer,
                          BigDecimal totalSales, String note) {
            // La mise à jour de l'objet est faite par CashService ; rien à faire ici.
        }

        @Override
        public List<CashSession> recent(int limit) {
            return new ArrayList<>(sessions);
        }
    }

    private final class FakeCashMovementDao extends CashMovementDao {

        @Override
        public long insert(CashMovement m) {
            long id = ++movementSeq[0];
            m.setId(id);
            cashMovements.add(m);
            return id;
        }

        @Override
        public List<CashMovement> listBySession(int sessionId) {
            return cashMovements.stream().filter(m -> m.getCashSessionId() == sessionId).toList();
        }

        @Override
        public BigDecimal sumByType(int sessionId, String type) {
            return cashMovements.stream()
                    .filter(m -> m.getCashSessionId() == sessionId)
                    .filter(m -> type.equals(m.getType()))
                    .map(CashMovement::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public long countBySession(int sessionId) {
            return cashMovements.stream().filter(m -> m.getCashSessionId() == sessionId).count();
        }
    }
}
