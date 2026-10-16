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

    public String shopName() {
        return get("shop.name", "TechShop");
    }

    public String shopAddress() {
        return get("shop.address", "");
    }

    public String shopPhone() {
        return get("shop.phone", "");
    }

    public String ticketFooter() {
        return get("ticket.footer", "Merci de votre confiance.");
    }
}
