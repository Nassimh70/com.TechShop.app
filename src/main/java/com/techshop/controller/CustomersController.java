package com.techshop.controller;

import com.techshop.dao.CustomerDao;
import com.techshop.model.Customer;
import com.techshop.service.PermissionDefaults;
import com.techshop.service.PermissionService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import javafx.scene.control.TableRow;

import java.sql.SQLException;

/**
 * Écran Clients (SPEC §4.7) : recherche, filtre « avec dette », création rapide
 * et ouverture de la fiche client (coordonnées, achats, réparations, paiements).
 */
public class CustomersController {

    @FXML private TextField searchField;
    @FXML private CheckBox debtFilter;
    @FXML private CheckBox inactifsFilter;
    @FXML private Label compteur;
    @FXML private HBox headerActions;
    @FXML private StackPane root;

    private final CustomerDao dao = new CustomerDao();
    private final ObservableList<Customer> clients = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        headerActions.getChildren().clear();
        if (PermissionService.get().can(PermissionDefaults.M_CUSTOMERS, PermissionService.Action.CREATE)) {
            Button nouveau = Buttons.primary("Nouveau client", "user-plus");
            nouveau.setOnAction(e -> nouveauClient());
            headerActions.getChildren().add(nouveau);
        }
        Button raf = Buttons.outline("Rafraîchir", "refresh-cw");
        raf.setOnAction(e -> charger());
        headerActions.getChildren().add(raf);

        searchField.textProperty().addListener((o, a, b) -> charger());
        debtFilter.selectedProperty().addListener((o, a, b) -> charger());
        inactifsFilter.selectedProperty().addListener((o, a, b) -> charger());

        root.getChildren().setAll(construireTableau());
        charger();
    }

    private void charger() {
        try {
            Boolean dette = debtFilter.isSelected() ? Boolean.TRUE : null;
            Boolean inactifs = inactifsFilter.isSelected() ? Boolean.TRUE : null;
            String q = searchField.getText() == null || searchField.getText().isBlank()
                    ? null : searchField.getText().trim();
            clients.setAll(dao.search(new CustomerDao.Filter(q, dette, inactifs)));
        } catch (SQLException e) {
            clients.clear();
        }
        long debiteurs = clients.stream().filter(c -> c.getBalance().signum() > 0).count();
        compteur.setText(clients.size() + " client" + (clients.size() > 1 ? "s" : "")
                + "  ·  " + debiteurs + " avec dette");
    }

    private TableView<Customer> construireTableau() {
        TableView<Customer> table = new TableView<>(clients);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(EmptyState.of("users", "Aucun client",
                "Ajoutez un client ou modifiez votre recherche."));

        TableColumn<Customer, String> cCode = new TableColumn<>("Code");
        cCode.setMinWidth(90);
        cCode.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getCode()));
        TableColumn<Customer, String> cNom = new TableColumn<>("Nom complet");
        cNom.setMinWidth(220);
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFullName()));
        TableColumn<Customer, String> cTel = new TableColumn<>("Téléphone");
        cTel.setMinWidth(140);
        cTel.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getPhone() == null ? "—" : d.getValue().getPhone()));
        TableColumn<Customer, String> cEmail = new TableColumn<>("Email");
        cEmail.setMinWidth(180);
        cEmail.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getEmail() == null ? "—" : d.getValue().getEmail()));
        TableColumn<Customer, String> cAdresse = new TableColumn<>("Adresse");
        cAdresse.setMinWidth(180);
        cAdresse.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().getAddress() == null ? "—" : d.getValue().getAddress()));
        TableColumn<Customer, String> cSolde = new TableColumn<>("Solde (dette)");
        cSolde.setMinWidth(130);
        cSolde.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getBalance())));
        cSolde.setCellFactory(colonneMontant());

        TableColumn<Customer, Customer> cAct = new TableColumn<>("");
        cAct.setMinWidth(150);
        cAct.setMaxWidth(190);
        cAct.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        cAct.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                setText(null);
                if (empty || c == null) {
                    setGraphic(null);
                    return;
                }
                Button fiche = Buttons.outline("Fiche");
                fiche.setOnAction(e -> ouvrirFiche(c));
                Button modif = Buttons.iconCompact("edit-2", "Modifier");
                modif.setOnAction(e -> modifier(c));
                HBox box = new HBox(6, fiche);
                if (c.getBalance().signum() > 0
                        && PermissionService.get().can(PermissionDefaults.M_CUSTOMERS,
                                PermissionService.Action.EDIT)) {
                    Button regler = Buttons.iconCompact("credit-card", "Encaisser un règlement");
                    regler.setOnAction(e -> DebtPaymentDialog.ouvrir(c, CustomersController.this::charger));
                    box.getChildren().add(regler);
                }
                box.getChildren().add(modif);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        table.getColumns().setAll(cCode, cNom, cTel, cEmail, cAdresse, cSolde, cAct);
        table.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    ouvrirFiche(row.getItem());
                }
            });
            return row;
        });
        return table;
    }

    private Callback<TableColumn<Customer, String>, TableCell<Customer, String>> colonneMontant() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String valeur, boolean empty) {
                super.updateItem(valeur, empty);
                setText(empty ? null : valeur);
                getStyleClass().remove("debt-amount");
                if (!empty && valeur != null && !valeur.startsWith("0,00")) {
                    getStyleClass().add("debt-amount");
                }
            }
        };
    }

    private void ouvrirFiche(Customer c) {
        CustomerDetailDialog.ouvrir(c, this::charger);
    }

    private void nouveauClient() {
        FormField nom = Fields.text("Nom complet", "OBLIGATOIRE");
        FormField tel = Fields.text("Téléphone", "Ex. 0550123456");
        FormField tel2 = Fields.text("Téléphone 2 (facultatif)", "");
        FormField email = Fields.text("Email (facultatif)", "contact@client.dz");
        FormField adr = Fields.area("Adresse (facultatif)", "");
        FormField notes = Fields.area("Notes (facultatif)", "");

        javafx.scene.layout.VBox corps = new javafx.scene.layout.VBox(10,
                nom, new HBox(10, tel, tel2), email, adr, notes);
        corps.setPrefWidth(460);

        Button annuler = Buttons.secondary("Annuler");
        Button creer = Buttons.primary("Créer le client", "user-plus");
        HBox pied = new HBox(8, annuler, creer);
        pied.getStyleClass().add("modal-footer");
        javafx.scene.layout.VBox carte = com.techshop.ui.components.DialogService.get()
                .card("Nouveau client", "user-plus", corps, pied);
        com.techshop.ui.components.DialogService.ModalHandle handle =
                com.techshop.ui.components.DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        creer.setOnAction(e -> {
            nom.clearError();
            if (nom.textValue().isBlank()) {
                nom.setError("Le nom est obligatoire.");
                return;
            }
            try {
                Customer cu = new Customer();
                cu.setFullName(nom.textValue().trim());
                cu.setPhone(tel.textValue().isBlank() ? null : tel.textValue().trim());
                cu.setPhone2(tel2.textValue().isBlank() ? null : tel2.textValue().trim());
                cu.setEmail(email.textValue().isBlank() ? null : email.textValue().trim());
                cu.setAddress(adr.textValue().isBlank() ? null : adr.textValue().trim());
                cu.setNotes(notes.textValue().isBlank() ? null : notes.textValue().trim());
                cu.setActive(true);
                dao.insert(cu);
                handle.close();
                ToastService.get().success("Client créé", cu.getFullName() + "  ·  " + cu.getCode());
                charger();
            } catch (RuntimeException ex) {
                nom.setError(ex.getMessage());
            } catch (SQLException ex) {
                nom.setError("Création impossible.");
            }
        });
        creer.setDefaultButton(true);
    }

    private void modifier(Customer c) {
        FormField nom = Fields.text("Nom complet", "OBLIGATOIRE");
        nom.setValue(c.getFullName());
        FormField tel = Fields.text("Téléphone", "");
        tel.setValue(c.getPhone() == null ? "" : c.getPhone());
        FormField tel2 = Fields.text("Téléphone 2 (facultatif)", "");
        tel2.setValue(c.getPhone2() == null ? "" : c.getPhone2());
        FormField email = Fields.text("Email (facultatif)", "");
        email.setValue(c.getEmail() == null ? "" : c.getEmail());
        FormField adr = Fields.area("Adresse (facultatif)", "");
        adr.setValue(c.getAddress() == null ? "" : c.getAddress());
        FormField notes = Fields.area("Notes (facultatif)", "");
        notes.setValue(c.getNotes() == null ? "" : c.getNotes());

        javafx.scene.layout.VBox corps = new javafx.scene.layout.VBox(10,
                nom, new HBox(10, tel, tel2), email, adr, notes);
        corps.setPrefWidth(460);
        Button annuler = Buttons.secondary("Annuler");
        Button enregistrer = Buttons.primary("Enregistrer", "save");
        HBox pied = new HBox(8, annuler, enregistrer);
        pied.getStyleClass().add("modal-footer");
        javafx.scene.layout.VBox carte = com.techshop.ui.components.DialogService.get()
                .card("Modifier le client", "edit-2", corps, pied);
        com.techshop.ui.components.DialogService.ModalHandle handle =
                com.techshop.ui.components.DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        enregistrer.setOnAction(e -> {
            nom.clearError();
            if (nom.textValue().isBlank()) {
                nom.setError("Le nom est obligatoire.");
                return;
            }
            try {
                c.setFullName(nom.textValue().trim());
                c.setPhone(tel.textValue().isBlank() ? null : tel.textValue().trim());
                c.setPhone2(tel2.textValue().isBlank() ? null : tel2.textValue().trim());
                c.setEmail(email.textValue().isBlank() ? null : email.textValue().trim());
                c.setAddress(adr.textValue().isBlank() ? null : adr.textValue().trim());
                c.setNotes(notes.textValue().isBlank() ? null : notes.textValue().trim());
                dao.update(c);
                handle.close();
                ToastService.get().success("Client modifié", c.getFullName());
                charger();
            } catch (RuntimeException ex) {
                nom.setError(ex.getMessage());
            } catch (SQLException ex) {
                nom.setError("Enregistrement impossible.");
            }
        });
        enregistrer.setDefaultButton(true);
    }
}
