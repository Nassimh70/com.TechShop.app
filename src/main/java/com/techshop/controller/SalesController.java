package com.techshop.controller;

import com.techshop.dao.SaleDao;
import com.techshop.model.Sale;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.service.SaleService;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.EmptyState;
import com.techshop.util.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class SalesController {

    @FXML private TextField searchField;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private ComboBox<String> statutFilter;
    @FXML private HBox headerActions;
    @FXML private VBox root;

    private final SaleService ventes = SaleService.get();
    private final PermissionService perms = PermissionService.get();
    private final ObservableList<Sale> lignes = FXCollections.observableArrayList();
    private SaleDao.Filter filtre = new SaleDao.Filter(null, null, null, null, null, null, null);
    private int page = 1;
    private int size = 15;
    private long total = 0;

    @FXML
    private void initialize() {
        headerActions.getChildren().clear();
        Button raf = Buttons.outline("Rafraîchir", "refresh-cw");
        raf.setOnAction(e -> charger());
        headerActions.getChildren().add(raf);

        statutFilter.getItems().setAll("Tous", "Payée", "Partielle", "Impayée", "Remboursée");
        statutFilter.getSelectionModel().selectFirst();
        fromDate.setValue(LocalDate.now().minusDays(29));
        toDate.setValue(LocalDate.now());
        searchField.textProperty().addListener((o,a,b) -> { page=1; charger(); });
        fromDate.valueProperty().addListener((o,a,b) -> { page=1; charger(); });
        toDate.valueProperty().addListener((o,a,b) -> { page=1; charger(); });
        statutFilter.valueProperty().addListener((o,a,b) -> { page=1; charger(); });

        charger();
    }

    private void charger() {
        SaleDao.Filter f = new SaleDao.Filter(
                searchField.getText() == null || searchField.getText().isBlank() ? null : searchField.getText().trim(),
                fromDate.getValue() == null ? null : fromDate.getValue(),
                toDate.getValue() == null ? null : toDate.getValue(),
                null, null,
                statutDepuis(statutFilter.getValue()),
                null);
        this.filtre = f;
        try {
            SaleService.SalesPage p = ventes.history(f, page, size);
            lignes.setAll(p.items());
            total = p.total();
        } catch (Exception e) {
            lignes.clear();
        }
        root.getChildren().setAll(construireTable());
    }

    private String statutDepuis(String v) {
        if (v == null) return null;
        return switch (v) {
            case "Payée" -> Sale.PAYEE;
            case "Partielle" -> Sale.PARTIELLE;
            case "Impayée" -> Sale.IMPAYEE;
            case "Remboursée" -> Sale.REMBOURSEE;
            default -> null;
        };
    }

    private Region construireTable() {
        TableView<Sale> table = new TableView<>(lignes);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(EmptyState.of("shopping-bag", "Aucune vente",
                "Aucune vente ne correspond aux filtres sélectionnés."));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Sale, String> cNum = new TableColumn<>("N°");
        cNum.setMinWidth(100);
        cNum.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getNumber()));
        TableColumn<Sale, String> cDate = new TableColumn<>("Date");
        cDate.setMinWidth(140);
        cDate.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.dateTime(d.getValue().getSaleDate())));
        TableColumn<Sale, String> cClient = new TableColumn<>("Client");
        cClient.setMinWidth(160);
        cClient.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getCustomerName() == null ? "Comptoir" : d.getValue().getCustomerName()));
        TableColumn<Sale, String> cVend = new TableColumn<>("Vendeur");
        cVend.setMinWidth(120);
        cVend.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getUsername() == null ? "—" : d.getValue().getUsername()));
        TableColumn<Sale, String> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(100);
        cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getTotal())));
        TableColumn<Sale, Sale> cStat = new TableColumn<>("Statut");
        cStat.setMinWidth(100);
        cStat.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cStat.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Sale s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                setGraphic(empty || s == null ? null : Badge.of(s.paymentStatusLabel(), switch (s.getPaymentStatus()) {
                    case Sale.PAYEE -> Badge.BadgeType.SUCCESS;
                    case Sale.PARTIELLE -> Badge.BadgeType.WARNING;
                    case Sale.IMPAYEE -> Badge.BadgeType.DANGER;
                    case Sale.REMBOURSEE -> Badge.BadgeType.NEUTRAL;
                    default -> Badge.BadgeType.NEUTRAL;
                }));
            }
        });
        TableColumn<Sale, String> cMethode = new TableColumn<>("Modes");
        cMethode.setMinWidth(120);
        cMethode.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().methodsLabel()));
        TableColumn<Sale, Sale> cAct = new TableColumn<>("");
        cAct.setMinWidth(120);
        cAct.setMaxWidth(160);
        cAct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cAct.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Sale s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                if (empty || s == null) { setGraphic(null); return; }
                Button voir = Buttons.iconCompact("eye", "Détail");
                voir.setOnAction(e -> voir(s));
                Button ticket = Buttons.iconCompact("printer", "Ticket");
                ticket.setOnAction(e -> imprimer(s));
                HBox box = new HBox(4, voir, ticket);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });
        table.getColumns().setAll(cNum, cDate, cClient, cVend, cTotal, cStat, cMethode, cAct);
        return table;
    }

    private void voir(Sale s) { SaleDetailDialog.ouvrir(s, this::charger); }
    private void imprimer(Sale s) {
        try {
            java.nio.file.Path p = com.techshop.service.SaleDocumentService.ticket(ventes.byId(s.getId()));
            com.techshop.service.SaleDocumentService.imprimer(p);
        } catch (Exception ex) {
            com.techshop.ui.components.ToastService.get().error("Impression", ex.getMessage());
        }
    }
}
