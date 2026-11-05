package com.techshop.license;

import dev.techshop.license.core.format.PublicKeyLoader;
import dev.techshop.license.core.model.SignedLicense;
import dev.techshop.license.core.model.VerificationResult;
import dev.techshop.license.core.verify.LicenseVerifier;
import dev.techshop.license.core.verify.VerificationContext;

import java.security.PublicKey;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generated for product "TechShop Manager" (techshop-manager).
 * Keep this file in sync with the public key exported by the license generator.
 */
public final class LicenseCheck_techshop_manager {

    public static final String PRODUCT_ID = "techshop-manager";
    public static final String PRODUCT_NAME = "TechShop Manager";

    private static final String PUBLIC_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA1d72IR19XNaGO60Qy7Yg
5ueeEEn81buRg58kHT2Vackl6RbUlyKEwM9ffFhwYx9bDJjJ5Al/r6tUsYNv6KhM
gZ4JDn/qnDYsAlLQPgRzDPAAy9f5FgyLDg/nUi58laI+lmCgoH3xRczlaAxg8JIC
98w1HFBpTr7mcvgzl9bX2DEXAbJElHyi4PxpvMGUTGbjIG5pJBfWiMLiPg+T7Hpg
/G2lbm8n9pVod8QMKyGSrrChiK4Ymj5X+tsJ7mj40Z9MomFjXhTx2/AU+C0tYPSa
O6sd5ymYzMCgHtpb69nW2FANl0GVLORdRUI7RX+JF+BwqgWbgcvBDOXvsy2gibvN
Ft6KOnhnS9/I5dRxxVeSccGhtOxI/zMQIpWaHwnjkZEkp6bwhPLNuiN9D2Nl5WwP
4sgYDjGzF3JqaJMNSao3kgnLqwAbO6vQ5LWaIn1uOTVfjZq1+abMLPrFOhOSKT7s
NO0p5DJTJQF5RG2YVlYD8DlfjzMttFNnGRRUtklsRJLdAgMBAAE=
-----END PUBLIC KEY-----
""";

    private LicenseCheck_techshop_manager() {
    }

    public static VerificationResult verify(SignedLicense license,
                                            String deviceId,
                                            Set<String> requiredFeatures) {
        try {
            PublicKey publicKey = PublicKeyLoader.fromPem(PUBLIC_KEY_PEM);
            LicenseVerifier verifier = new LicenseVerifier(
                    publicKey, Pattern.compile("^[0-9a-f]{64}$"));
            VerificationContext context = VerificationContext.of(
                    PRODUCT_ID, PRODUCT_NAME, deviceId, LocalDate.now())
                    .withRequiredFeatures(requiredFeatures);
            return verifier.verify(license, context);
        } catch (RuntimeException ex) {
            return new VerificationResult(false,
                    dev.techshop.license.core.model.LicenseStatus.INVALID,
                    java.util.List.of("License could not be verified"));
        }
    }
}
