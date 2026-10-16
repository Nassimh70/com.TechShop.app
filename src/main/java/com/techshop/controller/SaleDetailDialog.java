package com.techshop.controller;

import com.techshop.model.Sale;
import com.techshop.model.SaleItem;
import com.techshop.service.SaleService;
import com.techshop.service.SaleDocumentService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.ToastService;
import com.techshop.util.Format;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class SaleDetailDialog {

    private SaleDetailDialog() {
    }

    public static void ouvrir(Sale s, Runnable apres) {
        Sale sale = SaleService.get().byId(s.getId());        TableView<SaleItem> table = new TableView<>(FXCollections.observableArrayList(sale.getItems()));
        table.getStyleClass().add("data-table");
        table.setPrefHeight(240);
        TableColumn<SaleItem, String> cProd = new TableColumn<>("Produit");
        cProd.setMinWidth(200);
        cProd.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getProductName()
                + (d.getValue().getImei() == null ? "" : "\nIMEI " + d.getValue().getImei())));
        TableColumn<SaleItem, Integer> cQte = new TableColumn<>("Qté");
        cQte.setMinWidth(70);
        cQte.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getQuantity()));
        TableColumn<SaleItem, String> cPu = new TableColumn<>("P.U.");
        cPu.setMinWidth(90);
        cPu.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getUnitPrice())));
        TableColumn<SaleItem, String> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(100);
        cTotal.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(Format.money(d.getValue().getTotal())));
        table.getColumns().setAll(cProd, cQte, cPu, cTotal);

        VBox corps = new VBox(8,
                new Label("N° : " + sale.getNumber()),
                new Label("Date : " + Format.dateTime(sale.getSaleDate())),
                new Label("Client : " + (sale.getCustomerName() == null ? "Comptoir" : sale.getCustomerName())),
                table);
        corps.setPrefWidth(560);

        Button ticket = Buttons.outline("Imprimer ticket", "printer");
        ticket.setOnAction(e -> {
            try {
                var p = SaleDocumentService.ticket(sale);
                SaleDocumentService.imprimer(p);
                ToastService.get().success("Impression", "Ticket envoyé.");
            } catch (Exception ex) {
                ToastService.get().error("Impression", ex.getMessage());
            }
        });
        Button facture = Buttons.outline("Facture A4", "file-text");
        facture.setOnAction(e -> {
            try {
                var p = SaleDocumentService.facture(sale);
                SaleDocumentService.imprimer(p);
                ToastService.get().success("Facture", "Facture générée.");
            } catch (Exception ex) {
                ToastService.get().error("Facture", ex.getMessage());
            }
        });
        Button retour = Buttons.danger("Retour / remboursement", "corner-down-left");
        retour.setOnAction(e -> RefundDialog.ouvrir(sale, () -> {
            if (apres != null) {
                apres.run();
            }
        }));
        Button fermer = Buttons.secondary("Fermer");
        HBox pied = new HBox(8, ticket, facture, retour, fermer);
        pied.getStyleClass().add("modal-footer");

        VBox carte = DialogService.get().card("Détail de la vente", "shopping-bag", corps, pied);
        DialogService.ModalHandle h = DialogService.get().custom(carte, false);
        fermer.setOnAction(e -> { h.close(); if (apres != null) apres.run(); });
    }
}
