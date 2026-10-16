package com.techshop.controller;

import com.techshop.dao.SaleDao;
import com.techshop.model.Product;
import com.techshop.model.Sale;
import com.techshop.service.CashService;
import com.techshop.service.DashboardService;
import com.techshop.ui.components.Badge;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.StatCard;
import com.techshop.util.Format;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/**
 * Tableau de bord (SPEC §4). Cartes de synthèse, ventes récentes, top produits,
 * alertes de stock et état de caisse, alimentés par {@link DashboardService}.
 */
public class DashboardController {

    @FXML private FlowPane statsPane;
    @FXML private VBox leftPane;
    @FXML private VBox rightPane;
    @FXML private HBox headerActions;

    private final DashboardService dash = DashboardService.get();

    @FXML
    private void initialize() {
        headerActions.getChildren().clear();
        Button raf = Buttons.outline("Rafraîchir", "refresh-cw");
        raf.setOnAction(e -> charger());
        headerActions.getChildren().add(raf);
        charger();
    }

    private void charger() {
        CashService.get().refreshState();
        DashboardService.DashboardData d = dash.load();

        statsPane.getChildren().clear();
        ajouterCarte("CA du jour", Format.money(d.todaySales()), d.todayCount() + " vente(s)");
        ajouterCarte("Bénéfice du jour", Format.money(d.todayProfit()), null);
        ajouterCarte("Panier moyen", Format.money(d.avgBasket()), null);
        ajouterCarte("Créances clients", Format.money(d.totalDebt()), d.customerCount() + " clients");
        ajouterCarte("Valeur du stock", Format.money(d.stockValue()), d.productCount() + " références");
        ajouterCarte("Caisse", d.cashOpen() ? "Ouverte" : "Fermée",
                d.cashOpen() ? d.cashNumber() + " · " + Format.money(d.cashExpected()) : "aucune session");

        leftPane.getChildren().clear();
        leftPane.getChildren().add(carteVentes(d));
        leftPane.getChildren().add(carteTop(d));
        VBox.setVgrow(leftPane.getChildren().get(0), Priority.ALWAYS);

        rightPane.getChildren().clear();
        rightPane.getChildren().add(carteAlertes(d));
        rightPane.getChildren().add(carteCaisse(d));
    }

    private Region carteVentes(DashboardService.DashboardData d) {
        VBox carte = carte("Ventes récentes");
        if (d.recentSales().isEmpty()) {
            carte.getChildren().add(muted("Aucune vente enregistrée."));
            return carte;
        }
        for (Sale s : d.recentSales()) {
            HBox ligne = new HBox(8);
            ligne.setAlignment(Pos.CENTER_LEFT);
            Label num = new Label(s.getNumber());
            num.getStyleClass().add("mono");
            Label client = new Label(s.getCustomerName() == null ? "Comptoir" : s.getCustomerName());
            client.getStyleClass().add("muted");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label montant = new Label(Format.money(s.getTotal()));
            montant.getStyleClass().add("money");
            ligne.getChildren().addAll(num, client, sp, montant);
            ligne.getStyleClass().add("list-row");
            carte.getChildren().add(ligne);
        }
        return carte;
    }

    private Region carteTop(DashboardService.DashboardData d) {
        VBox carte = carte("Top produits (30 jours)");
        if (d.topProducts().isEmpty()) {
            carte.getChildren().add(muted("Pas encore de ventes ce mois-ci."));
            return carte;
        }
        for (SaleDao.TopProduct t : d.topProducts()) {
            HBox ligne = new HBox(8);
            ligne.setAlignment(Pos.CENTER_LEFT);
            Label nom = new Label(t.name());
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label qte = new Label(Format.count(t.quantity()) + " vendus");
            qte.getStyleClass().add("muted");
            Label montant = new Label(Format.money(t.total()));
            montant.getStyleClass().add("money");
            ligne.getChildren().addAll(nom, qte, sp, montant);
            ligne.getStyleClass().add("list-row");
            carte.getChildren().add(ligne);
        }
        return carte;
    }

    private Region carteAlertes(DashboardService.DashboardData d) {
        VBox carte = carte("Alertes de stock");
        if (d.alerts().isEmpty()) {
            Badge ok = Badge.of("Stock sain", Badge.BadgeType.SUCCESS);
            carte.getChildren().add(ok);
            return carte;
        }
        for (Product p : d.alerts()) {
            HBox ligne = new HBox(8);
            ligne.setAlignment(Pos.CENTER_LEFT);
            Label nom = new Label(p.getName());
            nom.setWrapText(true);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.SOMETIMES);
            boolean rupture = p.getQuantity() <= 0;
            Label q = new Label(rupture ? "Rupture" : p.getQuantity() + " restant(s)");
            q.getStyleClass().add(rupture ? "danger-text" : "warning-text");
            ligne.getChildren().addAll(nom, sp, q);
            ligne.getStyleClass().add("list-row");
            carte.getChildren().add(ligne);
        }
        return carte;
    }

    private Region carteCaisse(DashboardService.DashboardData d) {
        VBox carte = carte("État de caisse");
        HBox etat = new HBox(8);
        etat.setAlignment(Pos.CENTER_LEFT);
        etat.getChildren().add(Badge.of(d.cashOpen() ? "Ouverte" : "Fermée",
                d.cashOpen() ? Badge.BadgeType.SUCCESS : Badge.BadgeType.NEUTRAL));
        if (d.cashOpen()) {
            Label num = new Label(d.cashNumber());
            num.getStyleClass().add("mono");
            etat.getChildren().add(num);
        }
        carte.getChildren().add(etat);
        HBox attendu = new HBox(8);
        attendu.setAlignment(Pos.CENTER_LEFT);
        attendu.getChildren().addAll(muted("Espèces attendues"), spacer(),
                montantLabel(d.cashExpected()));
        carte.getChildren().add(attendu);
        HBox semaine = new HBox(8);
        semaine.setAlignment(Pos.CENTER_LEFT);
        semaine.getChildren().addAll(muted("CA 7 jours"), spacer(),
                montantLabel(d.weekTotal()));
        carte.getChildren().add(semaine);
        HBox mois = new HBox(8);
        mois.setAlignment(Pos.CENTER_LEFT);
        mois.getChildren().addAll(muted("CA du mois"), spacer(),
                montantLabel(d.monthTotal()));
        carte.getChildren().add(mois);

        Button ouvrir = Buttons.secondary("Gérer la caisse", "briefcase");
        ouvrir.setOnAction(e -> {
            var nav = com.techshop.service.NavigationService.get();
            var items = com.techshop.service.PermissionService.get()
                    .visibleItems(com.techshop.service.SessionContext.get().getRole());
            items.stream().filter(i -> "cash".equals(i.getId())).findFirst()
                    .ifPresent(nav::navigate);
        });
        carte.getChildren().add(ouvrir);
        return carte;
    }

    // ------------------------------------------------------------- helpers

    private VBox carte(String titre) {
        VBox carte = new VBox(8);
        carte.getStyleClass().add("card");
        Label t = new Label(titre);
        t.getStyleClass().add("card-title");
        carte.getChildren().add(t);
        return carte;
    }

    private Label muted(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("muted");
        return l;
    }

    private Label montantLabel(BigDecimal v) {
        Label l = new Label(Format.money(v));
        l.getStyleClass().add("money");
        return l;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private void ajouterCarte(String titre, String val, String sousTitre) {
        StatCard sc = new StatCard(titre, val);
        sc.setPrefWidth(180);
        sc.setMaxWidth(220);
        statsPane.getChildren().add(sc);
    }
}
