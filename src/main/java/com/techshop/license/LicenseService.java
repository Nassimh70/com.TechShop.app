package com.techshop.license;

import dev.techshop.license.core.format.LicenseFileIO;
import dev.techshop.license.core.model.LicensePayload;
import dev.techshop.license.core.model.SignedLicense;
import dev.techshop.license.core.model.VerificationResult;
import dev.techshop.license.core.verify.LicenseVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline licence gate for TechShop Manager.
 *
 * <p>The signed {@code .lic} file is installed in the user's local application
 * data folder (never in the installation directory, which may be read-only).
 * Every call to {@link #current()} re-reads and cryptographically verifies the
 * file against the public key embedded in {@link LicenseCheck_techshop_manager}
 * and against this computer's {@link DeviceId}.</p>
 */
public final class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    public static final String PRODUCT_ID = LicenseCheck_techshop_manager.PRODUCT_ID;
    public static final String PRODUCT_NAME = LicenseCheck_techshop_manager.PRODUCT_NAME;
    private static final String FILE_NAME = "techshop-manager.lic";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public enum State {
        VALID,
        EXPIRING_SOON,
        EXPIRED,
        NOT_YET_VALID,
        DEVICE_MISMATCH,
        INVALID,
        UNREADABLE,
        MISSING;

        public boolean isAccepted() {
            return this == VALID || this == EXPIRING_SOON;
        }
    }

    /** Immutable snapshot returned to the startup gate and the settings page. */
    public record LicenseStatus(State state,
                                SignedLicense license,
                                VerificationResult result,
                                Path file,
                                String message) {

        public boolean isAccepted() {
            return state.isAccepted();
        }

        public boolean isPresent() {
            return license != null;
        }

        public String activityLabel() {
            return switch (state) {
                case VALID -> "Licence active";
                case EXPIRING_SOON -> "Licence active - expire bientôt";
                case EXPIRED -> "Licence expirée";
                case NOT_YET_VALID -> "Licence pas encore valide";
                case DEVICE_MISMATCH -> "Licence liée à un autre ordinateur";
                case INVALID -> "Licence invalide";
                case UNREADABLE -> "Fichier de licence illisible";
                case MISSING -> "Aucune licence installée";
            };
        }

        public String badgeStyle() {
            return switch (state) {
                case VALID -> "badge-success";
                case EXPIRING_SOON, NOT_YET_VALID -> "badge-warning";
                case EXPIRED, DEVICE_MISMATCH, INVALID, UNREADABLE -> "badge-danger";
                case MISSING -> "badge-neutral";
            };
        }

        public String customerName() {
            return license == null ? null : license.payload().customerName();
        }

        public String licenseTypeLabel() {
            if (license == null || license.payload().licenseType() == null) {
                return null;
            }
            return switch (license.payload().licenseType()) {
                case ANNUAL -> "Annuelle";
                case MONTHLY -> "Mensuelle";
                case TRIAL -> "Essai";
                case CUSTOM_DURATION -> "Durée personnalisée";
                case PERPETUAL -> "Perpétuelle";
            };
        }

        public String issuedAtLabel() {
            return license == null ? null : DATE.format(license.payload().issuedAt());
        }

        public String expiresAtLabel() {
            LocalDate expiry = expiresAt();
            return expiry == null ? "Sans expiration" : DATE.format(expiry);
        }

        public LocalDate expiresAt() {
            return license == null ? null : license.payload().expiresAt();
        }

        public long daysRemaining() {
            return license == null ? 0 : LicenseVerifier.daysRemaining(license.payload(), LocalDate.now());
        }

        public List<String> features() {
            return license == null ? List.of() : license.payload().features();
        }

        public String diagnostic() {
            return result == null || result.errors().isEmpty()
                    ? "" : String.join("; ", result.errors());
        }
    }

    /** Raised when an imported file cannot be accepted as the active licence. */
    public static final class LicenseInstallException extends RuntimeException {
        private final transient LicenseStatus status;

        public LicenseInstallException(LicenseStatus status) {
            super(status == null ? "Licence refusée" : status.message());
            this.status = status;
        }

        public LicenseStatus status() {
            return status;
        }
    }

    private LicenseService() {
    }

    /** Writable location of the installed licence. */
    public static Path primaryFile() {
        return licenseDirectory().resolve(FILE_NAME);
    }

    /** Writable folder used for licence installation and diagnostics. */
    public static Path licenseDirectory() {
        String override = System.getProperty("techshop.license.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "TechShopManager", "license");
            }
        }
        return Path.of(System.getProperty("user.home", "."), ".techshop-manager", "license");
    }

    public static String deviceId() {
        return DeviceId.get();
    }

    /**
     * Reads the licence currently visible to the application. A licence placed
     * next to the executable or in the legacy {@code data/license} folder is
     * still recognised, so existing installations keep working.
     */
    public static LicenseStatus current() {
        Path file = locate();
        if (file == null) {
            return new LicenseStatus(State.MISSING, null, null, primaryFile(),
                    "Aucune licence n'est installée sur cet ordinateur.");
        }
        return inspect(file);
    }

    /** Verifies a file without installing it. */
    public static LicenseStatus inspect(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new LicenseStatus(State.UNREADABLE, null, null, primaryFile(),
                    "Le fichier de licence est introuvable.");
        }
        SignedLicense license;
        try {
            license = new LicenseFileIO().read(file);
        } catch (RuntimeException ex) {
            log.warn("Licence illisible ({}): {}", file, ex.getMessage());
            return new LicenseStatus(State.UNREADABLE, null, null, file,
                    "Le fichier de licence est illisible ou endommagé.");
        }

        String device = deviceId();
        VerificationResult result = LicenseCheck_techshop_manager.verify(license, device, Set.of());
        State state = map(result);
        String message = message(state, license, result);
        LicenseStatus status = new LicenseStatus(state, license, result, file, message);
        if (state.isAccepted()) {
            log.info("Licence {} pour {} (expire: {}) - appareil {}",
                    state, license.payload().customerName(), license.payload().expiresAt(), device);
        } else {
            log.warn("Licence refusée ({}) : {}", state, result.errors());
        }
        return status;
    }

    /**
     * Verifies a candidate file and installs it only when accepted. The
     * existing licence is replaced atomically; a rejected file never touches
     * the installed copy.
     */
    public static LicenseStatus install(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new LicenseInstallException(new LicenseStatus(State.UNREADABLE, null, null,
                    primaryFile(), "Le fichier de licence est introuvable."));
        }

        LicenseStatus candidate = inspect(source);
        if (!candidate.isAccepted()) {
            throw new LicenseInstallException(candidate);
        }

        Path target = primaryFile();
        Path directory = target.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".license-", ".tmp");
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveIntoPlace(temporary, target);
            temporary = null;

            LicenseStatus installed = inspect(target);
            if (!installed.isAccepted()) {
                throw new LicenseInstallException(installed);
            }
            log.info("Licence installée dans {}", target);
            return installed;
        } catch (IOException ex) {
            log.error("Installation de la licence impossible", ex);
            throw new LicenseInstallException(new LicenseStatus(State.UNREADABLE, null, null,
                    target, "Impossible d'enregistrer la licence sur cet ordinateur : " + ex.getMessage()));
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    /** Deletes the installed copy (used by administrators before a reinstall). */
    public static void uninstall() throws IOException {
        Files.deleteIfExists(primaryFile());
        log.info("Licence locale supprimée de {}", primaryFile());
    }

    // --------------------------------------------------------------- internals

    private static Path locate() {
        Path primary = primaryFile();
        if (Files.isRegularFile(primary)) {
            return primary;
        }
        String userDir = System.getProperty("user.dir", ".");
        for (Path legacy : List.of(
                Path.of(userDir, "data", "license", FILE_NAME),
                Path.of(userDir, FILE_NAME))) {
            if (Files.isRegularFile(legacy)) {
                return legacy;
            }
        }
        return null;
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State map(VerificationResult result) {
        if (result == null) {
            return State.INVALID;
        }
        if (!result.signatureValid()) {
            return State.INVALID;
        }
        dev.techshop.license.core.model.LicenseStatus status = result.status();
        if (status == null) {
            return State.INVALID;
        }
        return switch (status) {
            case VALID -> State.VALID;
            case EXPIRING_SOON -> State.EXPIRING_SOON;
            case EXPIRED -> State.EXPIRED;
            case NOT_YET_VALID -> State.NOT_YET_VALID;
            case DEVICE_MISMATCH -> State.DEVICE_MISMATCH;
            case INVALID -> State.INVALID;
        };
    }

    private static String message(State state, SignedLicense license, VerificationResult result) {
        LicensePayload payload = license.payload();
        return switch (state) {
            case VALID -> "Licence active pour " + payload.customerName() + ".";
            case EXPIRING_SOON -> "Licence active pour " + payload.customerName()
                    + ". Elle expire le " + DATE.format(payload.expiresAt()) + ".";
            case EXPIRED -> "La licence a expiré le " + DATE.format(payload.expiresAt()) + ".";
            case NOT_YET_VALID -> "La licence sera valide à partir du "
                    + DATE.format(payload.issuedAt()) + ".";
            case DEVICE_MISMATCH -> "Cette licence est liée à un autre ordinateur. "
                    + "Demandez une licence pour l'identifiant affiché ci-dessous.";
            case INVALID -> "La licence est invalide ou ne correspond pas à ce produit.";
            case UNREADABLE -> "Le fichier de licence est illisible ou endommagé.";
            case MISSING -> "Aucune licence n'est installée sur cet ordinateur.";
        };
    }
}
