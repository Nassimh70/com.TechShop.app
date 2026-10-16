package com.techshop.ui.components;

import com.techshop.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/**
 * Champs de formulaire réutilisables (SPEC §2) : texte, mot de passe avec œil,
 * nombre, date, liste déroulante, recherche avec icône, zone de texte.
 * Chaque champ porte son libellé et son message d'erreur sous le contrôle
 * (validation avec message sous le champ).
 */
public final class Fields {

    private Fields() {
    }

    /** Champ groupé : libellé + contrôle + message d'erreur. */
    public static FormField text(String label, String prompt) {
        return of(label, new TextField(), prompt);
    }

    public static FormField number(String label, String prompt) {
        TextField tf = new TextField();
        FormField f = of(label, tf, prompt);
        // Que des chiffres et une virgule décimale (saisie française)
        tf.textProperty().addListener((obs, old, v) -> {
            String clean = v == null ? "" : v.replaceAll("[^0-9,.]", "");
            if (!clean.equals(v)) {
                tf.setText(clean);
            }
        });
        return f;
    }

    /** Zone de texte multi-lignes. */
    public static FormField area(String label, String prompt) {
        var area = new javafx.scene.control.TextArea();
        area.getStyleClass().addAll("field", "text-area");
        area.setWrapText(true);
        area.setPromptText(prompt);
        return of(label, area, null);
    }

    /** Liste déroulante (items arbitraires, affichage via toString). */
    public static FormField combo(String label, java.util.List<?> items) {
        var combo = new javafx.scene.control.ComboBox<>();
        combo.getStyleClass().add("field");
        combo.setMaxWidth(Double.MAX_VALUE);
        if (items != null) {
            combo.getItems().setAll(items);
        }
        return of(label, combo, null);
    }

    /** Champ de mot de passe avec bouton « œil ». */
    public static FormField password(String label) {
        PasswordField pwd = new PasswordField();
        pwd.getStyleClass().add("field");
        TextField hidden = new TextField(); // révélé quand l'œil est actif
        hidden.getStyleClass().add("field");
        hidden.setVisible(false);
        hidden.setManaged(false);

        // Synchronisation dans les deux sens
        pwd.textProperty().bindBidirectional(hidden.textProperty());

        var eye = new javafx.scene.control.ToggleButton();
        eye.getStyleClass().add("field-inline-btn");
        eye.setGraphic(Icons.feather("eye", 15));
        eye.setTooltip(new javafx.scene.control.Tooltip("Afficher / masquer le mot de passe"));
        eye.selectedProperty().addListener((o, a, selected) -> {
            pwd.setVisible(!selected);
            pwd.setManaged(!selected);
            hidden.setVisible(selected);
            hidden.setManaged(selected);
            eye.setGraphic(Icons.feather(selected ? "eye-off" : "eye", 15));
            if (selected) {
                hidden.requestFocus();
                hidden.positionCaret(hidden.getText() == null ? 0 : hidden.getText().length());
            } else {
                pwd.requestFocus();
            }
        });

        StackPane box = new StackPane(pwd, hidden, eye);
        StackPane.setAlignment(eye, Pos.CENTER_RIGHT);
        eye.setTranslateX(-6);
        box.getStyleClass().add("input-wrap");

        FormField f = new FormField(label, box, pwd);
        pwd.getStyleClass().add("field");
        hidden.getStyleClass().add("field");
        pwd.setPrefWidth(Double.MAX_VALUE);
        hidden.setPrefWidth(Double.MAX_VALUE);
        return f;
    }

    /** Champ de recherche avec icône de loupe à gauche. */
    public static FormField search(String prompt) {
        TextField tf = new TextField();
        tf.getStyleClass().add("field");
        tf.setPromptText(prompt);
        var icon = Icons.feather("search", 15);
        HBox box = new HBox(icon, tf);
        box.getStyleClass().add("input-wrap");
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tf, Priority.ALWAYS);
        tf.setPadding(new javafx.geometry.Insets(0, 12, 0, 8));
        return new FormField(null, box, tf);
    }

    /** Champ date simple (DatePicker stylé en champ). */
    public static FormField date(String label) {
        var picker = new javafx.scene.control.DatePicker();
        picker.getStyleClass().add("field");
        picker.setMaxWidth(Double.MAX_VALUE);
        return of(label, picker, null);
    }

    // -------------------------------- Interne ---------------------------------

    private static FormField of(String label, javafx.scene.control.Control control, String prompt) {
        control.getStyleClass().add("field");
        if (prompt != null && control instanceof TextField tf) {
            tf.setPromptText(prompt);
        }
        control.setMaxWidth(Double.MAX_VALUE);
        return new FormField(label, control, control);
    }

    /** Marque un champ en erreur avec son message (SPEC §6 : validation). */
    public static void bindError(FormField f, java.util.function.Consumer<String> ignored) {
        // réservé aux prochaines phases (validation liée aux services)
    }
}
