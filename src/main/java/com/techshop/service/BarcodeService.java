package com.techshop.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Génération et rendu des codes-barres (SPEC §4.5, §4.6 — ZXing).
 * Un code interne EAN-13 (préfixe 200) est généré automatiquement à partir de
 * l'identifiant du produit ; les codes non EAN-13 sont rendus en CODE 128.
 */
public final class BarcodeService {

    /** Préfixe interne des codes EAN-13 (plage réservée aux usages internes). */
    private static final String PREFIXE = "200";

    private BarcodeService() {
    }

    /** Code EAN-13 valide et unique dérivé de l'identifiant produit. */
    public static String generateEan13(int productId) {
        long n = Math.floorMod((long) productId, 1_000_000_000L);
        String base = PREFIXE + String.format("%09d", n);
        return base + checkDigit(base);
    }

    /** Clé de contrôle EAN-13 d'une base de 12 chiffres. */
    public static char checkDigit(String base12) {
        int somme = 0;
        for (int i = 0; i < 12; i++) {
            int d = base12.charAt(i) - '0';
            somme += (i % 2 == 0) ? d : d * 3;
        }
        int reste = somme % 10;
        return (char) ('0' + (reste == 0 ? 0 : 10 - reste));
    }

    /** Un code valide au format EAN-13 (13 chiffres, clé correcte) ? */
    public static boolean isEan13(String code) {
        if (code == null || code.length() != 13) {
            return false;
        }
        for (int i = 0; i < 13; i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return checkDigit(code.substring(0, 12)) == code.charAt(12);
    }

    /** Image matricielle du code-barres (EAN-13 si valide, sinon CODE 128). */
    public static BufferedImage toImage(String code, int largeur, int hauteur) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code-barres vide.");
        }
        BarcodeFormat format = isEan13(code) ? BarcodeFormat.EAN_13 : BarcodeFormat.CODE_128;
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrice = new MultiFormatWriter().encode(code, format, largeur, hauteur, hints);
            return MatrixToImageWriter.toBufferedImage(matrice);
        } catch (WriterException e) {
            throw new IllegalStateException("Génération du code-barres impossible : "
                    + e.getMessage(), e);
        }
    }

    /** Image PNG du code-barres (pour l'insertion dans un PDF). */
    public static byte[] toPng(String code, int largeur, int hauteur) {
        try {
            ByteArrayOutputStream flux = new ByteArrayOutputStream();
            ImageIO.write(toImage(code, largeur, hauteur), "png", flux);
            return flux.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Encodage PNG du code-barres impossible.", e);
        }
    }
}
