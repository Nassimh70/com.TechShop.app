package com.techshop.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertTrue(scripts.contains("V001__schema.sql"));
        assertTrue(scripts.contains("V002__reference_data.sql"));
        assertFalse(scripts.contains("V003__demo_data.sql"));
    }

    @Test
    void demoProfileConserveLesSeeds() {
        System.setProperty("techshop.profile", "demo");
        var scripts = MigrationService.scriptsForProfile();

        assertTrue(scripts.contains("V001__schema.sql"));
        assertTrue(scripts.contains("V002__reference_data.sql"));
        assertTrue(scripts.contains("V003__demo_data.sql"));
    }

    @Test
    void versionEtDescription() {
        assertEquals("001", MigrationService.version("V001__schema.sql"));
        assertEquals("schema", MigrationService.description("V001__schema.sql"));
        assertEquals("reference data",
                MigrationService.description("V002__reference_data.sql"));
    }

    @Test
    void splitStatementsRespecteLesTriggers() {
        String script = """
                CREATE TABLE t (id INTEGER PRIMARY KEY, updated_at TEXT);
                CREATE TRIGGER trg AFTER UPDATE ON t
                BEGIN
                    UPDATE t SET updated_at = datetime('now','localtime') WHERE id = NEW.id;
                END;
                INSERT INTO t (id) VALUES (1);
                """;
        var instructions = MigrationService.splitStatements(script);
        assertEquals(3, instructions.size());
        assertTrue(instructions.get(1).contains("CREATE TRIGGER"));
        assertTrue(instructions.get(1).trim().endsWith("END"));
    }
}
