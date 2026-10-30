package com.techshop.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {

    @Test
    void lesCheminsPersistantsSontSousLeDossierApplication() {
        assertTrue(AppPaths.dataDirectory().startsWith(AppPaths.appDataDirectory()));
        assertTrue(AppPaths.configDirectory().startsWith(AppPaths.appDataDirectory()));
        assertTrue(AppPaths.portableMariaDbDirectory().startsWith(AppPaths.appDataDirectory()));
    }

    @Test
    void leRuntimePointeVersLeBinaireWindowsAttendu() {
        assertTrue(PortableDatabaseService.executable().getFileName().toString()
                .equals("mariadbd.exe"));
    }
}
