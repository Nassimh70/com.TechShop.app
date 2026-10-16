package com.techshop.controller;

import com.techshop.dao.CustomerDao;
import com.techshop.model.Customer;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.EmptyState;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.util.ValidationException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sélecteur de client : recherche (code, nom, téléphone) et ajout rapide de
 * client. Retourne le {@link Customer} sélectionné ou {@code null} pour « Comptoir ».
 */
public final class CustomerPickerDialog {

    private CustomerPickerDialog() {
    }

    public static void ouvrir(Consumer<Customer> callback) {
        CustomerDao dao = new CustomerDao();
        ObservableList<Customer> clients = FXCollections.observableArrayList();
        try {
            clients.addAll(dao.search(new CustomerDao.Filter(null, null, null)));
        } catch (SQLException ignored) {
        }

        TableView<Customer> table = new TableView<>(clients);
        table.getStyleClass().add("data-table");
        table.setPrefHeight(240);
        table.setPlaceholder(EmptyState.of("users", "Aucun client", "Recherchez un client ou créez-en un nouveau."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Customer, String> cCode = new TableColumn<>("Code");
        cCode.setMinWidth(100);
        cCode.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getCode()));
        TableColumn<Customer, String> cNom = new TableColumn<>("Nom");
        cNom.setMinWidth(200);
        cNom.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getFullName()));
        TableColumn<Customer, String> cTel = new TableColumn<>("Téléphone");
        cTel.setMinWidth(130);
        cTel.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getPhone()));
        TableColumn<Customer, String> cSolde = new TableColumn<>("Solde");
        cSolde.setMinWidth(100);
        cSolde.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                com.techshop.util.Format.money(d.getValue().getBalance())));
        table.getColumns().setAll(cCode, cNom, cTel, cSolde);

        TextField rech = new TextField();
        rech.setPromptText("Rechercher par nom, code ou téléphone…");
        rech.getStyleClass().add("field");
        HBox.setHgrow(rech, Priority.ALWAYS);
        rech.textProperty().addListener((o, a, b) -> rafraichir(dao, clients, b));

        VBox ajout = new VBox(8, new Label("Ajout rapide"), formAjout(dao, clients, rech, callback));
        ajout.getStyleClass().add("card-inset");

        VBox corps = new VBox(10, rech, table, ajout);
        corps.setPrefWidth(560);

        Button annuler = Buttons.secondary("Annuler");
        Button comptoir = Buttons.ghost("Comptoir");
        Button choisir = Buttons.primary("Choisir ce client", "check");
        HBox pied = new HBox(8, comptoir, annuler, choisir);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Sélectionner un client", "users", corps, pied);
        DialogService.ModalHandle handle = DialogService.get().custom(carte, false);

        annuler.setOnAction(e -> handle.close());
        comptoir.setOnAction(e -> {
            handle.close();
            if (callback != null) callback.accept(null);
        });
        choisir.setOnAction(e -> {
            Customer sel = table.getSelectionModel().getSelectedItem();
            handle.close();
            if (callback != null) callback.accept(sel);
        });
        choisir.setDefaultButton(true);
    }

    private static VBox formAjout(CustomerDao dao, ObservableList<Customer> clients, TextField rech,
                                  Consumer<Customer> callback) {
        FormField nom = Fields.text("Nom complet", "OBLIGATOIRE");
        FormField tel = Fields.text("Téléphone", "Ex. 0550123456");
        FormField tel2 = Fields.text("Téléphone 2 (facultatif)", "");
        FormField email = Fields.text("Email (facultatif)", "contact@client.dz");
        FormField adr = Fields.area("Adresse (facultatif)", "");
        Button creer = Buttons.outline("Créer et sélectionner", "user-plus");
        creer.setOnAction(e -> {
            try {
                nom.clearError();
                if (nom.textValue().isBlank()) {
                    nom.setError("Le nom est obligatoire.");
                    return;
                }
                Customer cu = new Customer();
                cu.setFullName(nom.textValue().trim());
                cu.setPhone(tel.textValue().trim());
                cu.setPhone2(tel2.textValue().isBlank() ? null : tel2.textValue().trim());
                cu.setEmail(email.textValue().isBlank() ? null : email.textValue().trim());
                cu.setAddress(adr.textValue().isBlank() ? null : adr.textValue().trim());
                cu.setActive(true);
                dao.insert(cu);
                // recharge
                rech.setText(cu.getFullName());
            } catch (ValidationException ex) {
                nom.setError(ex.getMessage());
            } catch (Exception ex) {
                nom.setError("Création impossible.");
            }
        });
        VBox v = new VBox(8, nom, new HBox(8, tel, tel2), email, adr, new HBox(creer));
        return v;
    }

    private static void rafraichir(CustomerDao dao, ObservableList<Customer> clients, String q) {
        try {
            String qq = q == null || q.isBlank() ? null : q.trim();
            clients.setAll(dao.search(new CustomerDao.Filter(qq, null, null)));
        } catch (Exception ignored) {
        }
    }
}
