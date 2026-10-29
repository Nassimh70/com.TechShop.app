package com.techshop.controller;

import com.techshop.dao.ReportDao;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.ToastService;
import com.techshop.util.ExportUtil;
import com.techshop.util.Format;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.io.File;
import java.time.LocalDate;
import java.util.*;

public class ReportsController {
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private ComboBox<String> cbReport;
    @FXML private TableView<List<Object>> table;
    @FXML private VBox chartPane;
    @FXML private VBox root;
    @FXML private TabPane tabs;

    private final ReportDao reportDao = new ReportDao();

    @FXML
    void initialize() {
        dpFrom.setValue(LocalDate.now().minusDays(29));
        dpTo.setValue(LocalDate.now());

        cbReport.setItems(FXCollections.observableArrayList(
                "1. Ventes par jour / période",
                "2. Top 10 produits vendus",
                "3. Ventes par caissier",
                "4. Mouvement de stock",
                "5. SAV / réparations",
                "6. Clients (meilleurs, en solde)",
                "7. Finances (encaisses, sorties, solde journalier)"
        ));
        cbReport.getSelectionModel().selectFirst();

        onRefresh();
    }

    @FXML
    void onRefresh() {
        runReport();
    }

    @FXML
    void onExportPdf() {
        try {
            File f = ExportUtil.exportPdf(table, "rapport.pdf");
            ToastService.get().success("Export PDF", "Fichier enregistré : " + f.getName());
        } catch (Exception e) {
            ToastService.get().error("Export PDF", e.getMessage());
        }
    }

    @FXML
    void onExportExcel() {
        try {
            File f = ExportUtil.exportExcel(table, "rapport.xlsx");
            ToastService.get().success("Export Excel", "Fichier enregistré : " + f.getName());
        } catch (Exception e) {
            ToastService.get().error("Export Excel", e.getMessage());
        }
    }

    @FXML
    void onPrint() {
        try {
            File f = ExportUtil.print(table, "rapport-a-imprimer.pdf");
            ToastService.get().success("Impression",
                    "Rapport envoyé à l'imprimante. PDF : " + f.getName());
        } catch (Exception e) {
            ToastService.get().error("Impression", e.getMessage());
        }
    }

    private void runReport() {
        String sel = cbReport.getSelectionModel().getSelectedItem();
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();
        if (sel == null) return;
        try {
            List<String> cols = new ArrayList<>();
            List<List<Object>> rows = new ArrayList<>();
            switch (sel) {
                case "1. Ventes par jour / période" -> {
                    cols = List.of("Date", "Nb ventes", "CA HT/total", "Moyenne");
                    var data = reportDao.salesByDay(from, to);
                    for (var d : data) rows.add(List.of(d.date(), d.count(), d.total(), d.average()));
                }
                case "2. Top 10 produits vendus" -> {
                    cols = List.of("Produit", "Réf", "Qté", "CA");
                    var data = reportDao.topProducts(from, to);
                    for (var d : data) rows.add(List.of(d.name(), d.ref(), d.qty(), d.total()));
                }
                case "3. Ventes par caissier" -> {
                    cols = List.of("Caissier", "Nb ventes", "CA");
                    var data = reportDao.salesByUser(from, to);
                    for (var d : data) rows.add(List.of(d.user(), d.count(), d.total()));
                }
                case "4. Mouvement de stock" -> {
                    cols = List.of("Produit", "Réf", "Entrées", "Sorties", "Solde");
                    var data = reportDao.stockMovements(from, to);
                    for (var d : data) rows.add(List.of(d.product(), d.ref(), d.in(), d.out(), d.balance()));
                }
                case "5. SAV / réparations" -> {
                    cols = List.of("Statut", "Nb", "Montant devis", "Montant final");
                    var data = reportDao.repairsSummary(from, to);
                    for (var d : data) rows.add(List.of(d.status(), d.count(), d.quoted(), d.finalAmount()));
                }
                case "6. Clients (meilleurs, en solde)" -> {
                    cols = List.of("Client", "Téléphone", "Achats", "CA client", "Solde");
                    var data = reportDao.topCustomers(from, to);
                    for (var d : data) rows.add(List.of(d.name(), d.phone(), d.orders(), d.total(), d.balance()));
                }
                case "7. Finances (encaisses, sorties, solde journalier)" -> {
                    cols = List.of("Date", "Encaissements", "Sorties", "Solde");
                    var data = reportDao.financesDaily(from, to);
                    for (var d : data) rows.add(List.of(d.date(), d.in(), d.out(), d.net()));
                }
                default -> {}
            }
            table.getColumns().clear();
            table.getItems().clear();
            for (int i = 0; i < cols.size(); i++) {
                final int idx = i;
                TableColumn<List<Object>, Object> c = new TableColumn<>(cols.get(i));
                c.setCellValueFactory(p -> new javafx.beans.property.SimpleObjectProperty<>(p.getValue().get(idx)));
                c.setCellFactory(col -> new TableCell<>() {
                    @Override
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else if (item instanceof java.math.BigDecimal bd) {
                            setText(Format.money(bd));
                        } else if (item instanceof LocalDate d) {
                            setText(Format.date(d));
                        } else if (item instanceof Long l && l <= 100000 && getTableColumn().getText().matches(".*ventes|Nb.*")) {
                            setText(String.valueOf(l));
                        } else {
                            setText(String.valueOf(item));
                        }
                    }
                });
                table.getColumns().add(c);
            }
            for (var r : rows) table.getItems().add(r);

            // Graphique : barres (1re colonne = catégorie, dernière colonne numérique = valeur)
            chartPane.getChildren().clear();
            if (!rows.isEmpty() && cols.size() >= 2) {
                javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
                javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
                javafx.scene.chart.BarChart<String, Number> chart =
                        new javafx.scene.chart.BarChart<>(xAxis, yAxis);
                chart.setTitle(sel);
                yAxis.setForceZeroInRange(true);
                javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
                series.setName(cols.get(cols.size() - 1));
                for (List<Object> r : rows) {
                    Object cat = r.get(0);
                    Object val = r.get(r.size() - 1);
                    if (val instanceof Number n) {
                        series.getData().add(new javafx.scene.chart.XYChart.Data<>(
                                String.valueOf(cat), n.doubleValue()));
                    }
                }
                chart.getData().add(series);
                VBox.setVgrow(chart, Priority.ALWAYS);
                chartPane.getChildren().add(chart);
            }
        } catch (Exception e) {
            ToastService.get().error("Rapport", e.getMessage());
        }
    }
}