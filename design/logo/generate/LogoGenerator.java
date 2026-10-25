import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Générateur de l'identité visuelle TechShop Manager.
 *
 * <p>Le dessin est 100 % vectoriel (Java2D) et rendu à chaque taille finale,
 * puis suréchantillonné : l'icône reste nette de 16 px à 1024 px. Le script
 * produit les SVG (sources éditables), les PNG, le .ico Windows et la planche
 * de présentation. Aucune dépendance externe.</p>
 *
 * <p>Exécution, depuis la racine du projet :</p>
 * <pre>java -Djava.awt.headless=true design/logo/generate/LogoGenerator.java</pre>
 */
public final class LogoGenerator {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final Path FONT_DIR = ROOT.resolve("src/main/resources/fonts");
    private static final Path OUT = ROOT.resolve("design/logo");
    private static final Path PNG = OUT.resolve("png");
    private static final Path RES = ROOT.resolve("src/main/resources/images/brand");

    // --- Jetons de marque -----------------------------------------------------
    private static final Color INDIGO_400 = new Color(0x7477F7);
    private static final Color INDIGO_500 = new Color(0x4F46E5);
    private static final Color INDIGO_700 = new Color(0x3730A3);
    private static final Color INDIGO_300 = new Color(0xA5B4FC);
    private static final Color INK = new Color(0x0F172A);
    private static final Color INK_SOFT = new Color(0x64748B);
    private static final Color PAPER = new Color(0xF8FAFC);
    private static final Color WHITE = new Color(0xFFFFFF);
    private static final Color NIGHT = new Color(0x0B1120);
    private static final Color NIGHT_SURFACE = new Color(0x111C2E);
    private static final Color NIGHT_BORDER = new Color(0x263449);

    private static final double ICON_SIZE = 512;
    private static final double RADIUS = 116;
    // Monogramme T : barre 280 × 76, hampe 80 × 248, centré sur 256.
    private static final double BAR_X = 116, BAR_Y = 132, BAR_W = 280, BAR_H = 76;
    private static final double STEM_X = 216, STEM_Y = 132, STEM_W = 80, STEM_H = 248;
    private static final double GLYPH_RADIUS = 30;

    private static Font regular;
    private static Font medium;
    private static Font semiBold;
    private static Font bold;

    private LogoGenerator() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(PNG);
        Files.createDirectories(RES);

        regular = load("Inter_400Regular.ttf");
        medium = load("Inter_500Medium.ttf");
        semiBold = load("Inter_600SemiBold.ttf");
        bold = load("Inter_700Bold.ttf");

        // --- Icône d'application (toutes tailles) ----------------------------
        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 96, 128, 180, 256, 512, 1024};
        for (int size : sizes) {
            writePng(icon(size), PNG.resolve("techshop-icon-" + size + ".png"));
            System.out.println("  png  techshop-icon-" + size + ".png");
        }

        // --- Icônes embarquées dans l'application ----------------------------
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            writePng(icon(size), RES.resolve("app-icon-" + size + ".png"));
        }
        writePng(icon(96), RES.resolve("techshop-mark-96.png"));
        writePng(icon(256), RES.resolve("techshop-mark-256.png"));

        // --- ICO Windows (BMP pour <= 128 px, PNG pour 256 px) ---------------
        List<Integer> icoSizes = List.of(16, 20, 24, 32, 40, 48, 64, 128, 256);
        List<BufferedImage> icoImages = new ArrayList<>();
        for (int size : icoSizes) {
            icoImages.add(icon(size));
        }
        byte[] ico = ico(icoImages);
        Files.write(OUT.resolve("techshop.ico"), ico);
        Files.write(RES.resolve("techshop.ico"), ico);
        System.out.println("  ico  techshop.ico (" + icoSizes + ")");

        // --- Logo horizontal clair / sombre ----------------------------------
        double titleWidth = textWidth(bold.deriveFont(68f), "TechShop")
                + 15 + textWidth(medium.deriveFont(68f), "Manager");
        int logoWidth = (int) Math.ceil(200 + titleWidth + 40) + 32;
        int logoHeight = 232;

        writePng(lockup(logoWidth, logoHeight, 1, false, 2),
                PNG.resolve("techshop-logo-light.png"));
        writePng(lockup(logoWidth, logoHeight, 1, true, 2),
                PNG.resolve("techshop-logo-dark.png"));
        writePng(lockup(logoWidth, logoHeight, 2, false, 1),
                PNG.resolve("techshop-logo-light@2x.png"));
        writePng(lockup(logoWidth, logoHeight, 2, true, 1),
                PNG.resolve("techshop-logo-dark@2x.png"));
        System.out.println("  png  techshop-logo-light/dark (1x + 2x)");

        // --- Sources SVG ------------------------------------------------------
        Files.writeString(OUT.resolve("techshop-mark.svg"), markSvg(), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("techshop-logo-light.svg"),
                logoSvg(logoWidth, logoHeight, false), StandardCharsets.UTF_8);
        Files.writeString(OUT.resolve("techshop-logo-dark.svg"),
                logoSvg(logoWidth, logoHeight, true), StandardCharsets.UTF_8);
        System.out.println("  svg  techshop-mark / logo-light / logo-dark");

        // --- Planche de présentation -----------------------------------------
        writePng(preview(), OUT.resolve("preview.png"));
        System.out.println("  png  preview.png");

        System.out.println("\nIdentité générée dans " + OUT);
        System.out.println("Ressources d'application dans " + RES);
    }

    // ================================================================ dessin

    /** Dessine l'icône dans un carré logique de 512 × 512. */
    private static void drawIcon(Graphics2D g) {
        drawIcon(g, true);
    }

    /**
     * @param homeIndicator true pour ajouter le détail « indicateur d'accueil ».
     *                      Il est omis sous 128 px, où il se transformerait en
     *                      encoche floue au bas du monogramme.
     */
    private static void drawIcon(Graphics2D g, boolean homeIndicator) {
        Shape tile = new RoundRectangle2D.Double(0, 0, ICON_SIZE, ICON_SIZE, RADIUS * 2, RADIUS * 2);

        Graphics2D c = (Graphics2D) g.create();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.clip(tile);

        // Dégradé indigo, du plus clair (haut gauche) au plus profond (bas droite)
        c.setPaint(new LinearGradientPaint(
                new Point2D.Double(52, 0), new Point2D.Double(468, 512),
                new float[]{0f, 0.52f, 1f},
                new Color[]{INDIGO_400, INDIGO_500, INDIGO_700}));
        c.fill(tile);

        // Lumière douce en haut à gauche : donne du relief sans effet « bouton »
        c.setPaint(new RadialGradientPaint(
                new Point2D.Double(132, 48), 390,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 58), new Color(255, 255, 255, 0)}));
        c.fill(tile);

        // Pied légèrement assombri : ancre l'icône sur les fonds clairs
        c.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, 250), new Point2D.Double(0, 512),
                new float[]{0f, 1f},
                new Color[]{new Color(12, 10, 60, 0), new Color(12, 10, 60, 34)}));
        c.fill(tile);

        // Filet intérieur : finition premium, lisible sur clair comme sur sombre
        c.setStroke(new BasicStroke(2.4f));
        c.setColor(new Color(255, 255, 255, 46));
        c.draw(new RoundRectangle2D.Double(2.2, 2.2, ICON_SIZE - 4.4, ICON_SIZE - 4.4,
                (RADIUS - 2.2) * 2, (RADIUS - 2.2) * 2));

        // Monogramme T (barre + hampe), blanc pur. L'indicateur d'accueil est
        // soustrait de la forme (et non « effacé ») : l'ouverture laisse voir
        // la tuile indigo, comme le masque des SVG, quelle que soit la taille.
        Shape monogram = monogram();
        if (homeIndicator) {
            java.awt.geom.Area glyph = new java.awt.geom.Area(monogram);
            glyph.subtract(new java.awt.geom.Area(
                    new RoundRectangle2D.Double(241, 326, 30, 6, 6, 6)));
            monogram = glyph;
        }
        c.setColor(WHITE);
        c.fill(monogram);

        c.dispose();
    }

    private static Shape monogram() {
        RoundRectangle2D bar = new RoundRectangle2D.Double(
                BAR_X, BAR_Y, BAR_W, BAR_H, BAR_H, BAR_H);
        RoundRectangle2D stem = new RoundRectangle2D.Double(
                STEM_X, STEM_Y, STEM_W, STEM_H, GLYPH_RADIUS * 2, GLYPH_RADIUS * 2);
        java.awt.geom.Area area = new java.awt.geom.Area(bar);
        area.add(new java.awt.geom.Area(stem));
        return area;
    }

    /** Dessine le logo horizontal (icône + nom) à l'échelle demandée. */
    private static void drawLockup(Graphics2D g, double x, double y, double scale, boolean dark) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale(scale, scale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // Icône 176 px, centrée verticalement sur la hauteur 200
        AffineTransform before = g.getTransform();
        g.translate(0, 12);
        g.scale(176 / ICON_SIZE, 176 / ICON_SIZE);
        drawIcon(g);
        g.setTransform(before);

        Color nameColor = dark ? new Color(0xF8FAFC) : INK;
        Color managerColor = dark ? INDIGO_300 : INDIGO_500;
        Color subColor = dark ? new Color(0x94A3B8) : INK_SOFT;

        Font nameFont = bold.deriveFont(68f);
        Font managerFont = medium.deriveFont(68f);
        double nameWidth = textWidth(nameFont, "TechShop");

        g.setFont(nameFont);
        g.setColor(nameColor);
        g.drawString("TechShop", 200, 100);

        g.setFont(managerFont);
        g.setColor(managerColor);
        g.drawString("Manager", (float) (200 + nameWidth + 15), 100);

        g.setFont(medium.deriveFont(17f));
        g.setColor(subColor);
        drawTracked(g, "GESTION COMMERCIALE", 202, 150, 3.4f);

        g.setTransform(old);
    }

    // ================================================================ images

    private static BufferedImage icon(int size) {
        int ss = size <= 128 ? 4 : (size <= 256 ? 2 : 1);
        boolean detail = size >= 128;
        return render(size, size, ss, ICON_SIZE, ICON_SIZE, g -> drawIcon(g, detail));
    }

    private static BufferedImage lockup(int width, int height, double multiplier,
                                        boolean dark, int ss) {
        int outWidth = (int) Math.round(width * multiplier);
        int outHeight = (int) Math.round(height * multiplier);
        return render(outWidth, outHeight, ss, width, height, g -> {
            g.translate(16, 16);
            drawLockup(g, 0, 0, 1, dark);
        });
    }

    private interface Painter {
        void paint(Graphics2D g);
    }

    /**
     * Rend le dessin en coordonnées logiques, suréchantillonné, puis le réduit
     * avec une interpolation bicubique : bords nets et réguliers à toutes les
     * tailles (16 px comme 1024 px).
     */
    private static BufferedImage render(int outWidth, int outHeight, int ss,
                                        double logicalWidth, double logicalHeight,
                                        Painter painter) {
        int bigWidth = outWidth * ss;
        int bigHeight = outHeight * ss;
        BufferedImage big = new BufferedImage(bigWidth, bigHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(bigWidth / logicalWidth, bigHeight / logicalHeight);
        painter.paint(g);
        g.dispose();

        if (ss == 1 && outWidth == bigWidth && outHeight == bigHeight) {
            return big;
        }
        BufferedImage out = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D o = out.createGraphics();
        o.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        o.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        o.drawImage(big, 0, 0, outWidth, outHeight, null);
        o.dispose();
        return out;
    }

    // ========================================================= planche visuelle

    private static BufferedImage preview() {
        int w = 1440;
        int h = 1010;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        g.setColor(PAPER);
        g.fillRect(0, 0, w, h);

        // En-tête
        AffineTransform before = g.getTransform();
        g.translate(64, 48);
        g.scale(64 / ICON_SIZE, 64 / ICON_SIZE);
        drawIcon(g);
        g.setTransform(before);

        g.setColor(INK);
        g.setFont(bold.deriveFont(30f));
        g.drawString("TechShop Manager", 148, 80);
        g.setColor(INK_SOFT);
        g.setFont(regular.deriveFont(15f));
        g.drawString("Identité visuelle — logo, icône d'application et usages", 148, 103);

        // Pastilles de palette
        Color[] palette = {INDIGO_500, INDIGO_400, INDIGO_700, INK, INK_SOFT};
        int dot = 20;
        int gap = 30;
        int start = 1376 - (palette.length - 1) * gap - dot;
        int px = start;
        for (Color color : palette) {
            g.setColor(color);
            g.fillOval(px, 72, dot, dot);
            px += gap;
        }
        g.setColor(INK_SOFT);
        g.setFont(medium.deriveFont(11f));
        drawTrackedRight(g, "PALETTE", start - 22, 87, 2.0f);

        // Carte claire
        card(g, 64, 150, 1312, 282, WHITE, new Color(0xE2E8F0));
        label(g, "SUR FOND CLAIR", 100, 188, INK_SOFT);
        drawLockup(g, 100, 214, 0.68, false);
        g.setColor(new Color(0xE2E8F0));
        g.drawLine(100, 378, 1340, 378);
        g.setColor(INK_SOFT);
        g.setFont(regular.deriveFont(12.5f));
        g.drawString("Logo principal sur surfaces claires — barre latérale, en-têtes, documents.", 100, 403);

        // Carte sombre
        card(g, 64, 456, 1312, 282, NIGHT, NIGHT_BORDER);
        label(g, "SUR FOND SOMBRE", 100, 494, new Color(0x94A3B8));
        drawLockup(g, 100, 520, 0.68, true);
        g.setColor(NIGHT_BORDER);
        g.drawLine(100, 684, 1340, 684);
        g.setColor(new Color(0x94A3B8));
        g.setFont(regular.deriveFont(12.5f));
        g.drawString("Variante sombre — thème nuit, écran de connexion, splash et documents foncés.", 100, 709);

        // Carte icône
        card(g, 64, 762, 1312, 196, WHITE, new Color(0xE2E8F0));
        label(g, "ICÔNE D'APPLICATION", 100, 800, INK_SOFT);

        int[] sizes = {16, 24, 32, 48, 64, 96, 128};
        int x = 100;
        int baseline = 918;
        for (int size : sizes) {
            BufferedImage icon = icon(size);
            int top = baseline - size;
            g.drawImage(icon, x, top, null);
            g.setColor(INK_SOFT);
            g.setFont(medium.deriveFont(11.5f));
            String caption = size + " px";
            g.drawString(caption, x + (size - g.getFontMetrics().stringWidth(caption)) / 2f, 938);
            x += Math.max(size, 72) + 8;
        }

        g.setColor(new Color(0xE2E8F0));
        g.drawLine(800, 800, 800, 912);
        g.setColor(INK);
        g.setFont(semiBold.deriveFont(17f));
        g.drawString("Lisible jusqu'à 16 px", 836, 838);
        g.setColor(INK_SOFT);
        g.setFont(regular.deriveFont(13f));
        g.drawString("Le monogramme T conserve sa silhouette", 836, 862);
        g.drawString("dans la barre des tâches et le menu Démarrer.", 836, 882);

        // Grande icône de référence
        BufferedImage big = icon(128);
        g.drawImage(big, 1180, 794, null);

        // Pied de page
        g.setColor(new Color(0xE2E8F0));
        g.drawLine(64, 970, 1376, 970);
        g.setColor(INK_SOFT);
        g.setFont(regular.deriveFont(12f));
        g.drawString("TechShop Manager · Identité de marque · Indigo 500 #4F46E5", 64, 990);
        drawTrackedRight(g, "DESKTOP · JAVAFX 21", 1376, 990, 1.4f);

        g.dispose();
        return img;
    }

    private static void card(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
        g.setColor(new Color(15, 23, 42, 10));
        g.fillRoundRect(x, y + 4, w, h, 22, 22);
        g.setColor(new Color(15, 23, 42, 6));
        g.fillRoundRect(x, y + 10, w, h, 22, 22);
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 22, 22);
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w - 1, h - 1, 22, 22);
    }

    private static void label(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(color);
        g.setFont(semiBold.deriveFont(11.5f));
        drawTracked(g, text, x, y, 2.2f);
    }

    // ================================================================ texte

    private static Font load(String file) throws Exception {
        try (var in = Files.newInputStream(FONT_DIR.resolve(file))) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }

    private static double textWidth(Font font, String text) {
        return font.getStringBounds(text, new java.awt.font.FontRenderContext(
                null, true, true)).getWidth();
    }

    private static void drawTracked(Graphics2D g, String text, double x, double y, float tracking) {
        AffineTransform old = g.getTransform();
        double cursor = x;
        Font font = g.getFont();
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g.drawString(ch, (float) cursor, (float) y);
            cursor += textWidth(font, ch) + tracking;
        }
        g.setTransform(old);
    }

    private static void drawTrackedRight(Graphics2D g, String text, double right, double y,
                                         float tracking) {
        Font font = g.getFont();
        double width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += textWidth(font, String.valueOf(text.charAt(i))) + tracking;
        }
        width -= tracking;
        drawTracked(g, text, right - width, y, tracking);
    }

    // ================================================================== SVG

    private static String markSvg() {
        return String.format(Locale.ROOT, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- TechShop Manager — icône d'application. Source vectorielle éditable. -->
                <svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512" role="img">
                  <title>TechShop Manager</title>
                  <defs>
                    %s
                  </defs>
                  <g clip-path="url(#mark-clip)">
                    <rect width="512" height="512" rx="116" fill="url(#mark-bg)"/>
                    <rect width="512" height="512" rx="116" fill="url(#mark-light)"/>
                    <rect width="512" height="512" rx="116" fill="url(#mark-shade)"/>
                  </g>
                  <rect x="2.2" y="2.2" width="507.6" height="507.6" rx="113.8"
                        fill="none" stroke="#FFFFFF" stroke-opacity=".18" stroke-width="2.4"/>
                  <path d="%s" fill="#FFFFFF" mask="url(#mark-mask)"/>
                </svg>
                """, iconDefs("mark"), monogramPath());
    }

    private static String logoSvg(int width, int height, boolean dark) {
        String nameColor = dark ? "#F8FAFC" : "#0F172A";
        String managerColor = dark ? "#A5B4FC" : "#4F46E5";
        String subColor = dark ? "#94A3B8" : "#64748B";
        String name = "TechShop";
        String manager = "Manager";
        double nameWidth = textWidth(bold.deriveFont(68f), name);
        double managerX = 200 + nameWidth + 15;
        String title = "TechShop Manager — logo horizontal " + (dark ? "sombre" : "clair");
        return String.format(Locale.ROOT, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- %s. Source vectorielle éditable. -->
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" role="img">
                  <title>%s</title>
                  <defs>
                    %s
                  </defs>
                  <g transform="translate(16 16)">
                    <g transform="translate(0 12) scale(0.34375)">
                      <g clip-path="url(#lock-clip)">
                        <rect width="512" height="512" rx="116" fill="url(#lock-bg)"/>
                        <rect width="512" height="512" rx="116" fill="url(#lock-light)"/>
                        <rect width="512" height="512" rx="116" fill="url(#lock-shade)"/>
                      </g>
                      <rect x="2.2" y="2.2" width="507.6" height="507.6" rx="113.8"
                            fill="none" stroke="#FFFFFF" stroke-opacity=".18" stroke-width="2.4"/>
                      <path d="%s" fill="#FFFFFF" mask="url(#lock-mask)"/>
                    </g>
                    <text x="200" y="100" font-family="Inter, 'Segoe UI', Arial, sans-serif"
                          font-size="68" font-weight="700" fill="%s">%s</text>
                    <text x="%.2f" y="100" font-family="Inter, 'Segoe UI', Arial, sans-serif"
                          font-size="68" font-weight="500" fill="%s">%s</text>
                    <text x="202" y="150" font-family="Inter, 'Segoe UI', Arial, sans-serif"
                          font-size="17" font-weight="500" letter-spacing="3.4" fill="%s">GESTION COMMERCIALE</text>
                  </g>
                </svg>
                """, title, width, height, width, height, title, iconDefs("lock"),
                monogramPath(), nameColor, name, managerX, managerColor, manager,
                subColor);
    }

    /** Définitions SVG du dégradé, de la lumière, du masque et du monogramme. */
    private static String iconDefs(String p) {
        return """
                <linearGradient id="%1$s-bg" x1="0.1" y1="0" x2="0.9" y2="1">
                      <stop offset="0" stop-color="#7477F7"/>
                      <stop offset="0.52" stop-color="#4F46E5"/>
                      <stop offset="1" stop-color="#3730A3"/>
                    </linearGradient>
                    <radialGradient id="%1$s-light" cx="0.26" cy="0.12" r="0.78">
                      <stop offset="0" stop-color="#FFFFFF" stop-opacity=".23"/>
                      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
                    </radialGradient>
                    <linearGradient id="%1$s-shade" x1="0" y1="0.49" x2="0" y2="1">
                      <stop offset="0" stop-color="#0C0A3C" stop-opacity="0"/>
                      <stop offset="1" stop-color="#0C0A3C" stop-opacity=".13"/>
                    </linearGradient>
                    <clipPath id="%1$s-clip">
                      <rect width="512" height="512" rx="116"/>
                    </clipPath>
                    <mask id="%1$s-mask">
                      <rect width="512" height="512" fill="#FFFFFF"/>
                      <rect x="241" y="326" width="30" height="6" rx="3" fill="#000000"/>
                    </mask>
                """.formatted(p);
    }

    private static String monogramPath() {
        return "M154 132 H358 A38 38 0 0 1 358 208 H296 V350 "
                + "A30 30 0 0 1 266 380 H246 A30 30 0 0 1 216 350 V208 "
                + "H154 A38 38 0 0 1 154 132 Z";
    }

    // ================================================================== ICO

    private static byte[] ico(List<BufferedImage> images) throws IOException {
        int count = images.size();
        List<byte[]> payloads = new ArrayList<>();
        for (BufferedImage image : images) {
            payloads.add(image.getWidth() >= 256 ? png(image) : bmp(image));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        u16(data, 0);
        u16(data, 1);
        u16(data, count);
        int offset = 6 + 16 * count;
        for (int i = 0; i < count; i++) {
            BufferedImage image = images.get(i);
            byte[] payload = payloads.get(i);
            data.writeByte(image.getWidth() >= 256 ? 0 : image.getWidth());
            data.writeByte(image.getHeight() >= 256 ? 0 : image.getHeight());
            data.writeByte(0);
            data.writeByte(0);
            u16(data, 1);
            u16(data, 32);
            u32(data, payload.length);
            u32(data, offset);
            offset += payload.length;
        }
        for (byte[] payload : payloads) {
            data.write(payload);
        }
        data.flush();
        return out.toByteArray();
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** Entier 16 bits little-endian (format ICO/BMP). */
    private static void u16(DataOutputStream data, int value) throws IOException {
        data.writeByte(value & 0xFF);
        data.writeByte((value >>> 8) & 0xFF);
    }

    /** Entier 32 bits little-endian (format ICO/BMP). */
    private static void u32(DataOutputStream data, int value) throws IOException {
        data.writeByte(value & 0xFF);
        data.writeByte((value >>> 8) & 0xFF);
        data.writeByte((value >>> 16) & 0xFF);
        data.writeByte((value >>> 24) & 0xFF);
    }

    /** Entrée ICO 32 bits (BITMAPINFOHEADER + pixels BGRA + masque AND vide). */
    private static byte[] bmp(BufferedImage image) throws IOException {
        int w = image.getWidth();
        int h = image.getHeight();
        int xorSize = w * h * 4;
        int maskRow = ((w + 31) / 32) * 4;
        int maskSize = maskRow * h;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        u32(data, 40);
        u32(data, w);
        u32(data, h * 2);
        u16(data, 1);
        u16(data, 32);
        u32(data, 0);
        u32(data, xorSize + maskSize);
        u32(data, 0);
        u32(data, 0);
        u32(data, 0);
        u32(data, 0);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                data.writeByte(argb & 0xFF);
                data.writeByte((argb >> 8) & 0xFF);
                data.writeByte((argb >> 16) & 0xFF);
                data.writeByte((argb >>> 24) & 0xFF);
            }
        }
        data.write(new byte[maskSize]);
        data.flush();
        return out.toByteArray();
    }

    // ================================================================= outils

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}



