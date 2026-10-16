package com.techshop.service;

import com.techshop.dao.CustomerDao;
import com.techshop.dao.ProductDao;
import com.techshop.dao.SaleDao;
import com.techshop.model.CashSession;
import com.techshop.model.Product;
import com.techshop.model.Sale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agrégats du tableau de bord (SPEC §4.1). Rassemble, en un seul appel, les
 * indicateurs du jour, la courbe des ventes de la semaine, la répartition des
 * encaissements, les meilleurs produits, l'état du stock et la caisse.
 */
public final class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final DashboardService INSTANCE = new DashboardService(
            new SaleDao(), new ProductDao(), new CustomerDao(), CashService.get(), StockService.get());

    private final SaleDao sales;
    private final ProductDao products;
    private final CustomerDao customers;
    private final CashService cash;
    private final StockService stock;

    public DashboardService(SaleDao sales, ProductDao products, CustomerDao customers,
                            CashService cash, StockService stock) {
        this.sales = sales;
        this.products = products;
        this.customers = customers;
        this.cash = cash;
        this.stock = stock;
    }

    public static DashboardService get() {
        return INSTANCE;
    }

    /** Photo complète du tableau de bord. */
    public record DashboardData(BigDecimal todaySales, long todayCount, BigDecimal todayProfit,
                                BigDecimal avgBasket, BigDecimal weekTotal, long weekCount,
                                BigDecimal monthTotal, BigDecimal totalDebt, long customerCount,
                                List<SaleDao.DayPoint> week, List<SaleDao.MethodPoint> breakdown,
                                List<SaleDao.TopProduct> topProducts, List<Sale> recentSales,
                                List<Product> alerts, BigDecimal stockValue, long productCount,
                                long lowStock, long outOfStock, boolean cashOpen,
                                BigDecimal cashExpected, String cashNumber) {

        /** Valeur maximale de la courbe (échelle). */
        public BigDecimal weekMax() {
            BigDecimal max = BigDecimal.ZERO;
            for (SaleDao.DayPoint p : week) {
                if (p.total().compareTo(max) > 0) {
                    max = p.total();
                }
            }
            return max;
        }
    }

    public DashboardData load() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        LocalDate weekStart = today.minusDays(6);
        LocalDateTime startOfWeek = weekStart.atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal todaySales = BigDecimal.ZERO;
        long todayCount = 0;
        BigDecimal todayProfit = BigDecimal.ZERO;
        BigDecimal weekTotal = BigDecimal.ZERO;
        long weekCount = 0;
        BigDecimal monthTotal = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        long customerCount = 0;
        List<SaleDao.DayPoint> week = new ArrayList<>();
        List<SaleDao.MethodPoint> breakdown = new ArrayList<>();
        List<SaleDao.TopProduct> top = new ArrayList<>();
        List<Sale> recent = List.of();
        List<Product> alerts = List.of();
        BigDecimal stockValue = BigDecimal.ZERO;
        long productCount = 0;
        long lowStock = 0;
        long outOfStock = 0;
        try {
            todaySales = sales.sumTotalBetween(startOfDay, endOfDay);
            todayCount = sales.countBetween(startOfDay, endOfDay);
            todayProfit = sales.sumProfitBetween(startOfDay, endOfDay);
            weekTotal = sales.sumTotalBetween(startOfWeek, endOfDay);
            weekCount = sales.countBetween(startOfWeek, endOfDay);
            monthTotal = sales.sumTotalBetween(startOfMonth, endOfDay);
            totalDebt = customers.totalDebt();
            customerCount = customers.countActive();
            week = sales.salesByDay(weekStart, today);
            breakdown = sales.paymentBreakdown(startOfWeek, endOfDay);
            top = sales.topProducts(startOfMonth, endOfDay, 5);
            recent = sales.recent(6);
            alerts = stock.alerts();
            stockValue = products.stockValueAtCost();
            productCount = products.countActive();
            lowStock = products.countLowStock();
            outOfStock = products.countOutOfStock();
        } catch (SQLException e) {
            log.error("Chargement du tableau de bord impossible", e);
        }

        BigDecimal avgBasket = todayCount > 0
                ? todaySales.divide(BigDecimal.valueOf(todayCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Optional<CashSession> session = cash.currentSession();
        boolean cashOpen = session.isPresent();
        BigDecimal cashExpected = BigDecimal.ZERO;
        String cashNumber = "";
        if (session.isPresent()) {
            cashNumber = session.get().getNumber();
            try {
                cashExpected = cash.state(session.get().getId(), session.get()).expectedCash();
            } catch (RuntimeException e) {
                cashExpected = session.get().getOpeningFloat();
            }
        }

        return new DashboardData(todaySales, todayCount, todayProfit, avgBasket, weekTotal, weekCount,
                monthTotal, totalDebt, customerCount, week, breakdown, top, recent, alerts, stockValue,
                productCount, lowStock, outOfStock, cashOpen, cashExpected, cashNumber);
    }
}
