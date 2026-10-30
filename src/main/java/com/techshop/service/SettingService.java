package com.techshop.service;

import com.techshop.dao.SettingDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paramètres de l'application (table « settings »). Lecture tolérante : en cas
 * d'indisponibilité, les valeurs par défaut de la SPEC sont renvoyées.
 */
public final class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    private static final SettingService INSTANCE = new SettingService(new SettingDao());

    private final SettingDao dao;

    public SettingService(SettingDao dao) {
        this.dao = dao;
    }

    public static SettingService get() {
        return INSTANCE;
    }

    public String get(String cle, String defaut) {
        try {
            return dao.get(cle).filter(v -> !v.isBlank()).orElse(defaut);
        } catch (Exception e) {
            log.warn("Lecture du paramètre « {} » impossible : {}", cle, e.getMessage());
            return defaut;
        }
    }

    public void set(String cle, String valeur) {
        try {
            dao.set(cle, valeur);
        } catch (Exception e) {
            log.error("Écriture du paramètre « {} » impossible", cle, e);
        }
    }

    public Map<String, String> all() {
        try {
            return dao.all();
        } catch (Exception e) {
            log.warn("Lecture des paramètres impossible : {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    // -------------------------------------------------------- raccourcis

    /** Première valeur non vide parmi plusieurs clés (migration douce). */
    private String getAny(String defaut, String... cles) {
        for (String cle : cles) {
            try {
                var valeur = dao.get(cle).filter(v -> !v.isBlank());
                if (valeur.isPresent()) {
                    return valeur.get();
                }
            } catch (Exception e) {
                log.warn("Lecture du paramètre « {} » impossible : {}", cle, e.getMessage());
            }
        }
        return defaut;
    }

    public String shopName() {
        return getAny("TechShop", "store.name", "shop.name");
    }

    public String shopAddress() {
        return getAny("", "store.address", "shop.address");
    }

    public String shopPhone() {
        return getAny("", "store.phone", "shop.phone");
    }

    public String shopEmail() {
        return getAny("", "store.email", "shop.email");
    }

    public String shopTaxId() {
        return getAny("", "store.tax_id", "shop.tax_id");
    }

    public String shopLogo() {
        return getAny("", "print.logo_path", "shop.logo");
    }

    public boolean autoPrintTicket() {
        return Boolean.parseBoolean(get("print.auto_ticket", "true"));
    }

    public boolean showVatOnTicket() {
        return Boolean.parseBoolean(get("sales.vat_on_ticket", "true"));
    }

    public boolean logoOnDocuments() {
        String logo = shopLogo();
        return Boolean.parseBoolean(get("print.logo", logo.isBlank() ? "false" : "true"));
    }

    public String ticketFooter() {
        return get("ticket.footer", "Merci de votre confiance.");
    }
}
