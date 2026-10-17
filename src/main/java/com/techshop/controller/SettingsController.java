package com.techshop.controller;

import com.techshop.config.ThemeManager;
import com.techshop.dao.SettingDao;
import com.techshop.ui.components.ToastService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class SettingsController {
    @FXML private TextField tfStoreName, tfStoreAddr, tfStorePhone, tfStoreEmail, tfStoreTaxId;
    @FXML private CheckBox cbTva, cbShowTvaTicket, cbPrintAuto, cbPrintLogo, cbDark, cbAutoBackup;
    @FXML private TextField tfTvaRate, tfPrefixSale, tfPrefixSav, tfNextNum, tfMinStock, tfMysqlBin;
    @FXML private Label lblLogo, lblBackup;
    private final SettingDao dao = new SettingDao();
    private String logoPath;

    @FXML
    void initialize() {
        tfMysqlBin.setText("C:\\xampp\\mysql\\bin");
        load();
    }

    private void load() {
        try {
            Map<String, String> m = dao.all();
            tfStoreName.setText(m.getOrDefault("store.name", ""));
            tfStoreAddr.setText(m.getOrDefault("store.address", ""));
            tfStorePhone.setText(m.getOrDefault("store.phone", ""));
            tfStoreEmail.setText(m.getOrDefault("store.email", ""));
            tfStoreTaxId.setText(m.getOrDefault("store.tax_id", ""));
            cbTva.setSelected("true".equals(m.get("sales.vat_enabled")));
            tfTvaRate.setText(m.getOrDefault("sales.vat_rate", "19"));
            cbShowTvaTicket.setSelected("true".equals(m.get("sales.vat_on_ticket")));
            tfPrefixSale.setText(m.getOrDefault("num.prefix_sale", "V"));
            tfPrefixSav.setText(m.getOrDefault("num.prefix_repair", "R"));
            tfNextNum.setText(m.getOrDefault("num.next", "100000"));
            cbPrintAuto.setSelected("true".equals(m.get("print.auto_ticket")));
            cbPrintLogo.setSelected("true".equals(m.get("print.logo")));
            logoPath = m.get("print.logo_path");
            lblLogo.setText(logoPath == null ? "Aucun logo" : new File(logoPath).getName());
            tfMinStock.setText(m.getOrDefault("stock.min", "5"));
            cbDark.setSelected("true".equals(m.get("theme.dark")));
            cbAutoBackup.setSelected("true".equals(m.get("backup.auto_on_exit")));
            tfMysqlBin.setText(m.getOrDefault("backup.mysql_bin", "C:\\xampp\\mysql\\bin"));
        } catch (Exception e) {
            ToastService.get().error("Paramètres", e.getMessage());
        }
    }

    @FXML
    void onSave() {
        try {
            dao.set("store.name", tfStoreName.getText());
            dao.set("store.address", tfStoreAddr.getText());
            dao.set("store.phone", tfStorePhone.getText());
            dao.set("store.email", tfStoreEmail.getText());
            dao.set("store.tax_id", tfStoreTaxId.getText());
            dao.set("sales.vat_enabled", String.valueOf(cbTva.isSelected()));
            dao.set("sales.vat_rate", tfTvaRate.getText());
            dao.set("sales.vat_on_ticket", String.valueOf(cbShowTvaTicket.isSelected()));
            dao.set("num.prefix_sale", tfPrefixSale.getText());
            dao.set("num.prefix_repair", tfPrefixSav.getText());
            dao.set("num.next", tfNextNum.getText());
            dao.set("print.auto_ticket", String.valueOf(cbPrintAuto.isSelected()));
            dao.set("print.logo", String.valueOf(cbPrintLogo.isSelected()));
            if (logoPath != null) dao.set("print.logo_path", logoPath);
            dao.set("stock.min", tfMinStock.getText());
            dao.set("theme.dark", String.valueOf(cbDark.isSelected()));
            dao.set("backup.auto_on_exit", String.valueOf(cbAutoBackup.isSelected()));
            dao.set("backup.mysql_bin", tfMysqlBin.getText());
            if (cbDark.isSelected()) com.techshop.config.ThemeManager.setTheme(com.techshop.config.ThemeManager.Theme.DARK); else com.techshop.config.ThemeManager.setTheme(com.techshop.config.ThemeManager.Theme.LIGHT);
            ToastService.get().success("Paramètres", "Enregistrés");
        } catch (Exception e) {
            ToastService.get().error("Paramètres", e.getMessage());
        }
    }

    @FXML
    void onChooseLogo() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(null);
        if (f != null) {
            try {
                Path dir = Path.of(System.getProperty("user.dir"), "data", "logo");
                Files.createDirectories(dir);
                Path target = dir.resolve(f.getName());
                Files.copy(f.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                logoPath = target.toString();
                lblLogo.setText(f.getName());
            } catch (Exception e) {
                ToastService.get().error("Logo", e.getMessage());
            }
        }
    }

    @FXML
    void onBrowseBin() {}

    @FXML
    void onBackupNow() { lblBackup.setText("Sauvegarde non implémentée dans cette version limitée"); }
    @FXML
    void onRestore() { lblBackup.setText("Restauration non implémentée dans cette version limitée"); }
}