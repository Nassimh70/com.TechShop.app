package com.techshop.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationServiceTest {

    @AfterEach
    void clearProfile() {
        System.clearProperty("techshop.profile");
    }

    @Test
    void productionExcludesDemoSeeds() {
        System.setProperty("techshop.profile", "prod");
        var scripts = MigrationService.scriptsForProfile();

        assertFalse(scripts.contains("V002__initial_data.sql"));
        assertFalse(scripts.contains("V003__product_seed.sql"));
        assertFalse(scripts.contains("V004__phase3_data.sql"));
        assertFalse(scripts.contains("V006__phase5.sql"));
        assertTrue(scripts.contains("V009__phase5_schema.sql"));
        assertTrue(scripts.contains("V010__production_reference_data.sql"));
    }

    @Test
    void demoProfileConserveLesSeeds() {
        System.setProperty("techshop.profile", "demo");
        var scripts = MigrationService.scriptsForProfile();

        assertTrue(scripts.contains("V002__initial_data.sql"));
        assertTrue(scripts.contains("V003__product_seed.sql"));
        assertTrue(scripts.contains("V004__phase3_data.sql"));
        assertTrue(scripts.contains("V006__phase5.sql"));
    }
}
