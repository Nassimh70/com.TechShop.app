package com.techshop.controller;

import com.techshop.model.Customer;
import com.techshop.model.PaymentMethod;
import com.techshop.service.SaleService;
import com.techshop.ui.components.Buttons;
import com.techshop.ui.components.DialogService;
import com.techshop.ui.components.Fields;
import com.techshop.ui.components.FormField;
import com.techshop.util.Format;
import com.techshop.util.Numbers;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Encaissement de la vente (SPEC §4.3) : composition des règlements (Espèces,
 * Carte, Virement, Crédit / partiel), pavé numérique, calcul en direct de
 * « payé / restant / rendu » et retour de la liste des
 * {@link SaleService.PaymentLine}. Le crédit (reste dû) exige un client : le
 * dialogue permet de le sélectionner, et la règle reste vérifiée par le
 * {@link SaleService}.
 */
public final class PaymentDialog {

    private PaymentDialog() {
    }

    /** Client rattaché à la vente et règlements saisis. */
    public record Result(Customer customer, List<SaleService.PaymentLine> payments) {
    }

    public static void ouvrir(BigDecimal total, Customer client, Consumer<Result> callback) {
        if (total == null || total.signum() <= 0) {
            if (callback != null) {
                callback.accept(new Result(client, List.of()));
            }
            return;
        }

        ToggleGroup mode = new ToggleGroup();
        ToggleButton esp = new ToggleButton("Espèces");
        ToggleButton carte = new ToggleButton("Carte");
        ToggleButton virement = new ToggleButton("Virement");
        ToggleButton credit = new ToggleButton("Crédit / partiel");
        esp.setToggleGroup(mode);
        carte.setToggleGroup(mode);
        virement.setToggleGroup(mode);
        credit.setToggleGroup(mode);
        esp.setSelected(true);
        HBox modeBar = new HBox(8, esp, carte, virement, credit);
        modeBar.getStyleClass().add("segmented");

        FormField montant = Fields.number("Montant à régler", "0,00");
        FormField recu = Fields.number("Espèces reçues", "0,00");
        TextField tfMontant = (TextField) montant.control();
        TextField tfRecu = (TextField) recu.control();
        montant.setValue(Format.moneyShort(total));

        Label totalL = new Label(Format.money(total));
        Label payeL = new Label(Format.money(BigDecimal.ZERO));
        Label resteL = new Label(Format.money(total));
        Label renduL = new Label(Format.money(BigDecimal.ZERO));
        Region[] refs = new Region[]{totalL, payeL, resteL, renduL};

        BigDecimal[] payeCourant = {BigDecimal.ZERO};
        ObservableList<Ligne> lignes = FXCollections.observableArrayList();
        TableView<Ligne> table = new TableView<>(lignes);
        table.getStyleClass().add("data-table");
        table.setPrefHeight(130);
        Label videReglements = new Label("Aucun règlement ajouté.");
        videReglements.getStyleClass().add("muted");
        table.setPlaceholder(videReglements);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Ligne, String> cMode = new TableColumn<>("Mode");
        cMode.setMinWidth(120);
        cMode.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(
                d.getValue().label()));
        TableColumn<Ligne, String> cMontant = new TableColumn<>("Montant");
        cMontant.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(
                Format.money(d.getValue().amount())));
        TableColumn<Ligne, Ligne> cAct = new TableColumn<>("");
        cAct.setMinWidth(52);
        cAct.setMaxWidth(52);
        cAct.setCellValueFactory(d -> new javafx.beans.property.ReadOnlyObjectWrapper<>(d.getValue()));
        cAct.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Ligne l, boolean empty) {
                super.updateItem(l, empty);
                setText(null);
                setGraphic(null);
                if (empty || l == null) {
                    return;
                }
                var sup = com.techshop.ui.components.Buttons.iconCompact("trash-2", "Retirer");
                sup.getStyleClass().add("btn-danger");
                sup.setOnAction(e -> retirer(lignes, payeCourant, total, refs, l, montant));
                setGraphic(sup);
            }
        });
        table.getColumns().setAll(List.of(cMode, cMontant, cAct));

        GridPane resume = new GridPane();
        resume.setHgap(12);
        resume.setVgap(6);
        resume.add(new Label("TOTAL"), 0, 0);
        resume.add(new Label("PAYÉ"), 0, 1);
        resume.add(new Label("RESTANT"), 0, 2);
        resume.add(new Label("RENDU"), 0, 3);
        resume.add(totalL, 1, 0);
        resume.add(payeL, 1, 1);
        resume.add(resteL, 1, 2);
        resume.add(renduL, 1, 3);
        resume.getStyleClass().add("card-inset");

        Customer[] clientRef = {client};
        Label clientLabel = new Label();
        clientLabel.setMaxWidth(Double.MAX_VALUE);
        clientLabel.setWrapText(true);
        Label clientError = new Label();
        clientError.getStyleClass().add("field-error-msg");
        clientError.setWrapText(true);
        clientError.setManaged(false);
        clientError.setVisible(false);
        Button choisirClient = com.techshop.ui.components.Buttons.outline("Choisir un client", "users");
        Runnable majClient = () -> {
            clientLabel.getStyleClass().removeAll("muted", "danger-text");
            if (clientRef[0] == null) {
                clientLabel.setText("Aucun client sélectionné (obligatoire pour le crédit).");
                clientLabel.getStyleClass().add("danger-text");
                choisirClient.setText("Choisir un client");
            } else {
                clientLabel.setText(clientRef[0].getFullName() + "  ·  " + clientRef[0].getCode());
                clientLabel.getStyleClass().add("muted");
                choisirClient.setText("Changer");
                clientError.setVisible(false);
                clientError.setManaged(false);
            }
        };
        majClient.run();
        choisirClient.setOnAction(e -> CustomerPickerDialog.ouvrir(sel -> {
            clientRef[0] = sel;
            majClient.run();
        }));
        HBox creditRow = new HBox(8, new Label("Client :"), clientLabel, choisirClient);
        creditRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(clientLabel, Priority.ALWAYS);
        VBox creditBox = new VBox(6, creditRow, clientError);
        creditBox.setManaged(false);
        creditBox.setVisible(false);

        mode.selectedToggleProperty().addListener((o, a, b) -> {
            boolean isEsp = b == esp;
            recu.setVisible(isEsp);
            recu.setManaged(isEsp);
            creditBox.setVisible(b == credit);
            creditBox.setManaged(b == credit);
        });

        Button ajouter = com.techshop.ui.components.Buttons.outline("Ajouter ce règlement", "plus");
        ajouter.setOnAction(e -> {
            try {
                String method = mode.getSelectedToggle() == carte ? PaymentMethod.CARTE.name()
                        : mode.getSelectedToggle() == virement ? PaymentMethod.VIREMENT.name()
                        : PaymentMethod.ESPECES.name();
                BigDecimal rest = total.subtract(payeCourant[0]);
                BigDecimal aRegler = montant.textValue() == null || montant.textValue().isBlank()
                        ? rest : Numbers.money(montant.textValue());
                boolean estCredit = mode.getSelectedToggle() == credit;
                if (estCredit) {
                    method = PaymentMethod.ESPECES.name();
                }
                if (aRegler.signum() <= 0 && payeCourant[0].signum() == 0) {
                    return; // rien à ajouter : laisser la vente entièrement en crédit
                }
                if (aRegler.compareTo(rest) > 0) {
                    aRegler = rest; // on ne règle jamais plus que le reste dû
                }
                if (mode.getSelectedToggle() == esp || estCredit) {
                    BigDecimal recuV = tfRecu.getText() == null || tfRecu.getText().isBlank()
                            ? aRegler : Numbers.money(tfRecu.getText());
                    if (recuV.compareTo(aRegler) < 0) {
                        recu.setError("Le montant reçu est inférieur au montant à régler.");
                        return;
                    }
                    ajouterLigne(lignes, payeCourant, total, refs, PaymentMethod.ESPECES.name(), aRegler);
                    majRendu(renduL, recuV.subtract(aRegler));
                } else {
                    ajouterLigne(lignes, payeCourant, total, refs, method, aRegler);
                }
                montant.clearError();
                recu.clearError();
                BigDecimal nouveauReste = total.subtract(payeCourant[0]);
                montant.setValue(nouveauReste.signum() > 0 ? Format.moneyShort(nouveauReste) : "0,00");
                tfRecu.clear();
            } catch (Exception ex) {
                montant.setError(ex.getMessage());
            }
        });

        Button resteBtn = com.techshop.ui.components.Buttons.ghost("Payer le reste");
        resteBtn.setOnAction(e -> {
            BigDecimal rest = total.subtract(payeCourant[0]);
            if (rest.signum() > 0) {
                montant.setValue(Format.moneyShort(rest));
                tfRecu.setText(Format.moneyShort(rest));
            }
        });

        HBox actions = new HBox(8, ajouter, resteBtn);

        Supplier<TextField> cible = () -> {
            if (tfRecu.isFocused() || (recu.isVisible() && !tfMontant.isFocused())) {
                return tfRecu;
            }
            return tfMontant;
        };
        GridPane pave = construirePave(cible);

        VBox centre = new VBox(10, modeBar, montant, recu, creditBox, actions, pave, table, resume);
        centre.setPrefWidth(440);

        Button annuler = com.techshop.ui.components.Buttons.secondary("Annuler");
        Button valider = com.techshop.ui.components.Buttons.primary("Valider et encaisser", "credit-card");
        HBox pied = new HBox(8, annuler, valider);
        pied.getStyleClass().add("modal-footer");

        VBox carteDlg = com.techshop.ui.components.DialogService.get()
                .card("Encaissement de la vente", "credit-card", centre, pied);
        var handle = com.techshop.ui.components.DialogService.get().custom(carteDlg, true);

        annuler.setOnAction(e -> handle.close());
        valider.setOnAction(e -> {
            BigDecimal reste = total.subtract(payeCourant[0]);
            if (reste.signum() > 0 && clientRef[0] == null) {
                creditBox.setVisible(true);
                creditBox.setManaged(true);
                clientError.setText("Sélectionnez un client : une vente avec un reste à payer "
                        + "doit être rattachée à un client.");
                clientError.setVisible(true);
                clientError.setManaged(true);
                return;
            }
            List<SaleService.PaymentLine> out = new ArrayList<>();
            for (Ligne l : lignes) {
                out.add(new SaleService.PaymentLine(l.method(), l.amount()));
            }
            handle.close();
            if (callback != null) {
                callback.accept(new Result(clientRef[0], out));
            }
        });
        valider.setDefaultButton(true);

        maj(payeCourant[0], total, refs);
    }

    private record Ligne(String method, BigDecimal amount) {
        String label() {
            return PaymentMethod.libelle(method);
        }
    }

    /** Pavé numérique pilotant le champ actif (montant ou espèces reçues). */
    private static GridPane construirePave(Supplier<TextField> cible) {
        GridPane pave = new GridPane();
        pave.setHgap(6);
        pave.setVgap(6);
        String[][] touches = {
                {"7", "8", "9"},
                {"4", "5", "6"},
                {"1", "2", "3"},
                {",", "0", "C"}
        };
        for (int r = 0; r < touches.length; r++) {
            for (int c = 0; c < touches[r].length; c++) {
                String t = touches[r][c];
                Button b = com.techshop.ui.components.Buttons.secondary(t);
                b.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(b, Priority.ALWAYS);
                b.setOnAction(e -> {
                    TextField tf = cible.get();
                    if (tf == null) {
                        return;
                    }
                    String actuel = tf.getText() == null ? "" : tf.getText();
                    if ("C".equals(t)) {
                        tf.clear();
                        return;
                    }
                    if (",".equals(t) && actuel.contains(",")) {
                        return;
                    }
                    tf.setText(actuel + t);
                    tf.positionCaret(tf.getText().length());
                });
                pave.add(b, c, r);
            }
        }
        return pave;
    }

    private static void ajouterLigne(ObservableList<Ligne> lignes, BigDecimal[] payeCourant,
                                     BigDecimal total, Region[] refs, String method, BigDecimal m) {
        lignes.add(new Ligne(method, m));
        payeCourant[0] = payeCourant[0].add(m);
        maj(payeCourant[0], total, refs);
    }

    private static void retirer(ObservableList<Ligne> lignes, BigDecimal[] payeCourant,
                                BigDecimal total, Region[] refs, Ligne l, FormField montant) {
        lignes.remove(l);
        payeCourant[0] = payeCourant[0].subtract(l.amount());
        if (payeCourant[0].signum() < 0) {
            payeCourant[0] = BigDecimal.ZERO;
        }
        maj(payeCourant[0], total, refs);
        BigDecimal rest = total.subtract(payeCourant[0]);
        montant.setValue(rest.signum() > 0 ? Format.moneyShort(rest) : "0,00");
    }

    private static void majRendu(Label renduL, BigDecimal rendu) {
        if (rendu.signum() < 0) {
            rendu = BigDecimal.ZERO;
        }
        renduL.setText(Format.money(rendu));
    }

    private static void maj(BigDecimal paye, BigDecimal total, Region[] refs) {
        if (refs[0] instanceof Label totalL) {
            totalL.setText(Format.money(total));
        }
        if (refs[1] instanceof Label payeL) {
            payeL.setText(Format.money(paye));
        }
        BigDecimal rest = total.subtract(paye);
        if (rest.signum() < 0) {
            rest = BigDecimal.ZERO;
        }
        if (refs[2] instanceof Label resteL) {
            resteL.setText(Format.money(rest));
        }
        BigDecimal rendu = paye.subtract(total);
        if (rendu.signum() < 0) {
            rendu = BigDecimal.ZERO;
        }
        if (refs[3] instanceof Label renduL) {
            renduL.setText(Format.money(rendu));
        }
    }
}
