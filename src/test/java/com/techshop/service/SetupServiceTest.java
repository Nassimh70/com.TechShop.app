package com.techshop.service;

import com.techshop.util.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SetupServiceTest {

    @Test
    void refuseUnNomDeMagasinVide() {
        assertThrows(ValidationException.class, () -> SetupService.get().initialize(
                new SetupService.SetupData("", "", "", "", "", "DZD", "19", "",
                        "Gérant", "gerant", "Motdepasse1", "Motdepasse1")));
    }

    @Test
    void refuseConfirmationDeMotDePasseDifferente() {
        assertThrows(ValidationException.class, () -> SetupService.get().initialize(
                new SetupService.SetupData("TechShop", "", "", "", "", "DZD", "19", "",
                        "Gérant", "gerant", "Motdepasse1", "different1")));
    }

    @Test
    void refuseIdentifiantInvalide() {
        assertThrows(ValidationException.class, () -> SetupService.get().initialize(
                new SetupService.SetupData("TechShop", "", "", "", "", "DZD", "19", "",
                        "Gérant", "g", "Motdepasse1", "Motdepasse1")));
    }
}
