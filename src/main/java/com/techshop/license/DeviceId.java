package com.techshop.license;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stable hardware identifier used by the offline licence.
 *
 * <p>The value must match the {@code ^[0-9a-f]{64}$} pattern configured for
 * the {@code techshop-manager} product in the licence generator. It is built
 * from identifiers that survive normal software updates: the Windows
 * {@code MachineGuid} first, then the SMBIOS UUID, then platform-specific
 * machine identifiers. MAC addresses are only used as a last-resort fallback
 * because adding a VPN or USB network adapter must not invalidate a licence.</p>
 *
 * <p>The raw identifiers are hashed with SHA-256 and never displayed. Only the
 * 64-character hexadecimal digest leaves this class, which is what the
 * customer sends to the vendor to receive a licence file.</p>
 */
public final class DeviceId {

    private static final Pattern REG_SZ = Pattern.compile("REG_SZ\\s+(\\S+)");
    private static final String FALLBACK_PREFIX = "fallback";

    private static volatile String cached;

    private DeviceId() {
    }

    /** Returns the cached 64-character lowercase hexadecimal device id. */
    public static String get() {
        String value = cached;
        if (value == null) {
            synchronized (DeviceId.class) {
                value = cached;
                if (value == null) {
                    cached = value = compute();
                }
            }
        }
        return value;
    }

    /** Recomputes the identifier (diagnostics only). */
    public static String refresh() {
        synchronized (DeviceId.class) {
            cached = compute();
            return cached;
        }
    }

    private static String compute() {
        String material = rawMaterial();
        return sha256Hex(material);
    }

    private static String rawMaterial() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String machineGuid = windowsMachineGuid();
            if (isUsable(machineGuid)) {
                return "windows-machine-guid:" + machineGuid.trim().toLowerCase(Locale.ROOT);
            }
            String smbios = windowsSmbiosUuid();
            if (isUsable(smbios)) {
                return "windows-smbios:" + smbios.trim().toLowerCase(Locale.ROOT);
            }
        } else if (os.contains("mac")) {
            String ioreg = macPlatformUuid();
            if (isUsable(ioreg)) {
                return "mac-platform:" + ioreg.trim().toLowerCase(Locale.ROOT);
            }
        } else {
            String machineId = unixMachineId();
            if (isUsable(machineId)) {
                return "unix-machine-id:" + machineId.trim().toLowerCase(Locale.ROOT);
            }
        }
        return fallbackMaterial();
    }

    // --------------------------------------------------------------- sources

    private static String windowsMachineGuid() {
        String output = run(
                List.of("reg.exe", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"),
                5);
        if (output == null) {
            return null;
        }
        Matcher matcher = REG_SZ.matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String windowsSmbiosUuid() {
        String output = run(List.of(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-CimInstance -ClassName Win32_ComputerSystemProduct).UUID"), 8);
        if (output == null || output.isBlank()) {
            output = run(List.of("wmic.exe", "csproduct", "get", "UUID"), 8);
            if (output == null) {
                return null;
            }
            String[] lines = output.split("\\R");
            for (String line : lines) {
                String candidate = line.trim();
                if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("UUID")) {
                    return candidate;
                }
            }
            return null;
        }
        return output.trim();
    }

    private static String macPlatformUuid() {
        String output = run(List.of("ioreg", "-rd1", "-c", "IOPlatformExpertDevice"), 5);
        if (output == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"").matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unixMachineId() {
        for (Path path : List.of(Path.of("/etc/machine-id"), Path.of("/var/lib/dbus/machine-id"))) {
            try {
                if (Files.isRegularFile(path)) {
                    String value = Files.readString(path, StandardCharsets.UTF_8).trim();
                    if (isUsable(value)) {
                        return value;
                    }
                }
            } catch (IOException ignored) {
                // try the next known location
            }
        }
        return null;
    }

    private static String fallbackMaterial() {
        StringBuilder material = new StringBuilder(FALLBACK_PREFIX);
        material.append("|host=").append(hostname());
        material.append("|os=").append(System.getProperty("os.name", ""));
        material.append("|arch=").append(System.getProperty("os.arch", ""));
        List<String> macs = macAddresses();
        for (String mac : macs) {
            material.append("|mac=").append(mac);
        }
        return material.toString();
    }

    private static String hostname() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) {
            name = System.getenv("HOSTNAME");
        }
        if (name == null || name.isBlank()) {
            try {
                name = java.net.InetAddress.getLocalHost().getHostName();
            } catch (IOException ignored) {
                name = "unknown";
            }
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<String> macAddresses() {
        List<String> values = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (network.isUp() && !network.isLoopback() && network.getHardwareAddress() != null) {
                    values.add(HexFormat.of().formatHex(network.getHardwareAddress()));
                }
            }
        } catch (Exception ignored) {
            // empty fallback list
        }
        Collections.sort(values);
        return values;
    }

    // --------------------------------------------------------------- helpers

    private static boolean isUsable(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("unknown")
                || normalized.equalsIgnoreCase("not applicable")
                || normalized.equalsIgnoreCase("to be filled by o.e.m.")) {
            return false;
        }
        String compact = normalized.replace("-", "").replace(" ", "");
        if (compact.matches("0+") || compact.matches("(?i)f+")) {
            return false;
        }
        return !normalized.contains(" ");
    }

    private static String run(List<String> command, int timeoutSeconds) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? output : null;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
