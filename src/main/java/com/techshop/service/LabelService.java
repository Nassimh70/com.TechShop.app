package com.techshop.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.techshop.model.ActivityActions;
import com.techshop.model.Product;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;

import java.awt.Desktop;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Génération et impression d'étiquettes code-barres (SPEC §4.5 — OpenPDF + ZXing).
 * Une planche A4 de vignettes (nom, code-barres, prix, référence) est produite,
 * puis imprimée via l'application système par défaut.
 */
public final class LabelService {

    private static final Font POLICE_NOM = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font POLICE_INFO = FontFactory.getFont(FontFactory.HELVETICA, 7);
    private static final Font POLICE_PRIX = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

    private LabelService() {
    }

    /**
     * Génère la planche d'étiquettes.
     *
     * @param produits produits à étiqueter
     * @param copies   nombre d'étiquettes par produit
     * @param avecPrix imprimer le prix de vente
     * @return le fichier PDF généré
     */
    public static Path generer(List<Product> produits, int copies, boolean avecPrix) throws IOException {
        if (produits == null || produits.isEmpty()) {
            throw new ValidationException("Aucun produit à étiqueter.");
        }
        int n = Math.max(1, Math.min(copies, 50));
        Path dossier = Files.createDirectories(
                Path.of(System.getProperty("user.dir"), "data", "etiquettes"));
        Path fichier = dossier.resolve("etiquettes-" + System.currentTimeMillis() + ".pdf");

        Document document = new Document(PageSize.A4, 24, 24, 24, 24);
        try (FileOutputStream flux = new FileOutputStream(fichier.toFile())) {
            PdfWriter.getInstance(document, flux);
            document.open();
            int colonnes = 3;
            PdfPTable table = new PdfPTable(colonnes);
            table.setWidthPercentage(100);
            table.setHorizontalAlignment(Element.ALIGN_CENTER);

            for (Product produit : produits) {
                for (int i = 0; i < n; i++) {
                    table.addCell(vignette(produit, avecPrix));
                }
            }
            table.completeRow();
            document.add(new Paragraph(" "));
            document.add(table);
        } catch (Exception e) {
            throw new IOException("Génération du PDF impossible : " + e.getMessage(), e);
        } finally {
            document.close();
        }
        ActivityLogService.get().log(ActivityActions.ETIQUETTES, "products", null,
                "Étiquettes générées (" + produits.size() + " produit(s) × " + n + ")");
        return fichier;
    }

    /** Imprime (ou ouvre) la planche générée via le système. */
    public static void imprimer(Path pdf) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new ValidationException("Impression non disponible sur ce système. "
                        + "Le PDF a été enregistré : " + pdf.toAbsolutePath());
            }
            Desktop bureau = Desktop.getDesktop();
            if (bureau.isSupported(Desktop.Action.PRINT)) {
                bureau.print(pdf.toFile());
            } else {
                bureau.open(pdf.toFile());
            }
        } catch (IOException e) {
            throw new ValidationException("Impression impossible : " + e.getMessage()
                    + " — PDF enregistré : " + pdf.toAbsolutePath());
        }
    }

    private static PdfPCell vignette(Product produit, boolean avecPrix) {
        PdfPTable interne = new PdfPTable(1);
        interne.setWidthPercentage(100);

        String nom = produit.getName() == null ? "—" : produit.getName();
        interne.addCell(sansBordure(new Phrase(tronquer(nom, 34), POLICE_NOM)));

        String code = produit.getBarcode() != null && !produit.getBarcode().isBlank()
                ? produit.getBarcode() : produit.getRef();
        try {
            byte[] png = BarcodeService.toPng(code, 320, 90);
            Image image = Image.getInstance(png);
            image.scaleToFit(140, 40);
            PdfPCell imageCell = new PdfPCell(image, false);
            imageCell.setBorder(PdfPCell.NO_BORDER);
            imageCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            imageCell.setPaddingBottom(2);
            interne.addCell(imageCell);
        } catch (Exception e) {
            interne.addCell(sansBordure(new Phrase(code, POLICE_INFO)));
        }

        String info = produit.getRef();
        if (avecPrix && produit.getSalePrice() != null) {
            interne.addCell(sansBordure(new Phrase(info, POLICE_INFO)));
            PdfPCell prix = sansBordure(new Phrase(Format.money(produit.getSalePrice()), POLICE_PRIX));
            prix.setHorizontalAlignment(Element.ALIGN_CENTER);
            interne.addCell(prix);
        } else {
            interne.addCell(sansBordure(new Phrase(info, POLICE_INFO)));
        }

        PdfPCell cellule = new PdfPCell(interne);
        cellule.setBorderWidth(0.5f);
        cellule.setPadding(6);
        cellule.setMinimumHeight(72);
        return cellule;
    }

    private static PdfPCell sansBordure(Phrase contenu) {
        PdfPCell cellule = new PdfPCell(contenu);
        cellule.setBorder(PdfPCell.NO_BORDER);
        cellule.setPadding(0);
        cellule.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cellule;
    }

    private static String tronquer(String texte, int max) {
        return texte.length() <= max ? texte : texte.substring(0, max - 1) + "…";
    }
}
