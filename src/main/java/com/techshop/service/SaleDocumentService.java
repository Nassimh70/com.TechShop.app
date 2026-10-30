package com.techshop.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.techshop.model.ActivityActions;
import com.techshop.model.Sale;
import com.techshop.model.SaleItem;
import com.techshop.model.SalePayment;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;

import java.awt.Desktop;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Documents d'une vente (SPEC §4.3, §4.4) : ticket de caisse 80 mm et facture
 * A4, générés avec OpenPDF. L'aperçu avant impression est fourni sous forme
 * texte ({@link #ticketText(Sale)}) ; l'impression passe par le système.
 */
public final class SaleDocumentService {

    /** Largeur d'un rouleau 80 mm en points (1 mm ≈ 2,8346 pt). */
    private static final float LARGEUR_TICKET = 80f * 2.8346f;

    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font GROS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NORMAL_B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font PETIT = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);
    private static final Font PETIT_B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f);

    private SaleDocumentService() {
    }

    // ------------------------------------------------------------- ticket 80mm

    /** Génère le ticket 80 mm d'une vente et renvoie le fichier PDF. */
    public static Path ticket(Sale sale) throws IOException {
        Path fichier = fichier("tickets", "ticket-" + sale.getNumber());
        Document doc = new Document(new Rectangle(LARGEUR_TICKET, 900), 8, 8, 10, 10);
        try (FileOutputStream flux = new FileOutputStream(fichier.toFile())) {
            PdfWriter.getInstance(doc, flux);
            doc.open();
            SettingService s = SettingService.get();
            logo(doc, Image.ALIGN_CENTER);

            doc.add(centre(s.shopName(), TITRE));
            if (!s.shopAddress().isBlank()) {
                doc.add(centre(s.shopAddress(), PETIT));
            }
            if (!s.shopPhone().isBlank()) {
                doc.add(centre("Tél : " + s.shopPhone(), PETIT));
            }
            if (!s.shopEmail().isBlank()) {
                doc.add(centre(s.shopEmail(), PETIT));
            }
            doc.add(espace());
            doc.add(centre("TICKET DE CAISSE", NORMAL_B));
            doc.add(espace());

            doc.add(ligne("N°", sale.getNumber()));
            doc.add(ligne("Date", sale.getSaleDate() == null ? "—"
                    : Format.dateTime(sale.getSaleDate())));
            doc.add(ligne("Vendeur", sale.getUsername() == null ? "—" : sale.getUsername()));
            doc.add(ligne("Client", sale.getCustomerName() == null ? "Comptoir" : sale.getCustomerName()));
            doc.add(separateur());

            for (SaleItem it : sale.getItems()) {
                doc.add(centre(it.getProductName(), PETIT_B));
                String q = it.getQuantity() + " × " + Format.money(it.getUnitPrice());
                doc.add(ligneValue(q, Format.money(it.getTotal())));
                if (it.getImei() != null && !it.getImei().isBlank()) {
                    doc.add(gauche("  IMEI : " + it.getImei(), PETIT));
                }
                if (it.getWarrantyMonths() > 0) {
                    doc.add(gauche("  Garantie : " + it.getWarrantyMonths() + " mois", PETIT));
                }
            }
            doc.add(separateur());
            doc.add(ligneValue("Sous-total", Format.money(sale.getSubtotal())));
            if (sale.getDiscount().signum() > 0) {
                doc.add(ligneValue("Remise", "-" + Format.money(sale.getDiscount())));
            }
            if (s.showVatOnTicket()) {
                doc.add(ligneValue("TVA (incluse)", Format.money(sale.getTax())));
            }
            doc.add(ligneValueGras("TOTAL", Format.money(sale.getTotal())));
            doc.add(separateur());
            for (SalePayment p : sale.getPayments()) {
                doc.add(ligneValue(p.methodLabel(), Format.money(p.getAmount())));
            }
            if (sale.getDueAmount().signum() > 0) {
                doc.add(ligneValue("Reste à payer", Format.money(sale.getDueAmount())));
            }
            doc.add(espace());
            doc.add(centre(s.ticketFooter(), PETIT));
            doc.add(centre("Merci de votre visite", PETIT));
        } catch (Exception e) {
            throw new IOException("Génération du ticket impossible : " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        ActivityLogService.get().log(ActivityActions.VENTE, "sales", sale.getId(),
                "Ticket imprimé — " + sale.getNumber());
        return fichier;
    }

    /** Aperçu texte du ticket (affiché avant impression). */
    public static String ticketText(Sale sale) {
        SettingService s = SettingService.get();
        StringBuilder b = new StringBuilder();
        b.append(centreTexte(s.shopName(), 40)).append('\n');
        if (!s.shopAddress().isBlank()) {
            b.append(centreTexte(s.shopAddress(), 40)).append('\n');
        }
        b.append(centreTexte("TICKET DE CAISSE", 40)).append('\n');
        b.append("----------------------------------------\n");
        b.append(String.format("N°     : %s%n", sale.getNumber()));
        b.append(String.format("Date   : %s%n", sale.getSaleDate() == null ? "—"
                : Format.dateTime(sale.getSaleDate())));
        b.append(String.format("Client : %s%n", sale.getCustomerName() == null
                ? "Comptoir" : sale.getCustomerName()));
        b.append("----------------------------------------\n");
        for (SaleItem it : sale.getItems()) {
            b.append(it.getProductName()).append('\n');
            b.append(String.format("  %d x %s%n", it.getQuantity(), Format.money(it.getUnitPrice())));
            if (it.getImei() != null && !it.getImei().isBlank()) {
                b.append("  IMEI : ").append(it.getImei()).append('\n');
            }
        }
        b.append("----------------------------------------\n");
        b.append(ligneTexte("Sous-total", Format.money(sale.getSubtotal())));
        if (sale.getDiscount().signum() > 0) {
            b.append(ligneTexte("Remise", "-" + Format.money(sale.getDiscount())));
        }
        if (s.showVatOnTicket()) {
            b.append(ligneTexte("TVA (incluse)", Format.money(sale.getTax())));
        }
        b.append(ligneTexte("TOTAL", Format.money(sale.getTotal())));
        for (SalePayment p : sale.getPayments()) {
            b.append(ligneTexte(p.methodLabel(), Format.money(p.getAmount())));
        }
        if (sale.getDueAmount().signum() > 0) {
            b.append(ligneTexte("Reste à payer", Format.money(sale.getDueAmount())));
        }
        b.append("----------------------------------------\n");
        b.append(centreTexte(s.ticketFooter(), 40)).append('\n');
        return b.toString();
    }

    // ---------------------------------------------------------------- facture A4

    /** Génère la facture A4 d'une vente et renvoie le fichier PDF. */
    public static Path facture(Sale sale) throws IOException {
        Path fichier = fichier("factures", "facture-" + sale.getNumber());
        Document doc = new Document(PageSize.A4, 40, 40, 42, 42);
        try (FileOutputStream flux = new FileOutputStream(fichier.toFile())) {
            PdfWriter.getInstance(doc, flux);
            doc.open();
            SettingService s = SettingService.get();
            logo(doc, Image.ALIGN_LEFT);

            PdfPTable entete = new PdfPTable(2);
            entete.setWidthPercentage(100);
            entete.setWidths(new float[]{3, 2});
            PdfPCell gauche = sansBordure(new Phrase(enteteMagasin(s), NORMAL));
            PdfPCell droite = sansBordure(new Phrase("FACTURE", GROS));
            droite.setHorizontalAlignment(Element.ALIGN_RIGHT);
            entete.addCell(gauche);
            entete.addCell(droite);
            doc.add(entete);
            doc.add(espace());

            PdfPTable infos = new PdfPTable(2);
            infos.setWidthPercentage(100);
            infos.addCell(info("Facture N°", sale.getNumber()));
            infos.addCell(info("Date", sale.getSaleDate() == null ? "—"
                    : Format.dateTime(sale.getSaleDate())));
            infos.addCell(info("Client", sale.getCustomerName() == null
                    ? "Comptoir" : sale.getCustomerName()));
            infos.addCell(info("Vendeur", sale.getUsername() == null ? "—" : sale.getUsername()));
            doc.add(infos);
            doc.add(espace());

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 1, 1.6f, 1.4f, 1.6f});
            entete(table, "Désignation");
            entete(table, "Qté");
            entete(table, "P.U.");
            entete(table, "Remise");
            entete(table, "Total");
            for (SaleItem it : sale.getItems()) {
                cellule(table, it.getProductName()
                        + (it.getImei() != null && !it.getImei().isBlank() ? "\nIMEI " + it.getImei() : ""),
                        Element.ALIGN_LEFT);
                cellule(table, String.valueOf(it.getQuantity()), Element.ALIGN_CENTER);
                cellule(table, Format.money(it.getUnitPrice()), Element.ALIGN_RIGHT);
                cellule(table, Format.money(it.getDiscount()), Element.ALIGN_RIGHT);
                cellule(table, Format.money(it.getTotal()), Element.ALIGN_RIGHT);
            }
            doc.add(table);
            doc.add(espace());

            PdfPTable totaux = new PdfPTable(2);
            totaux.setWidthPercentage(40);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totaux.setWidths(new float[]{2, 2});
            total(totaux, "Sous-total", Format.money(sale.getSubtotal()), NORMAL);
            if (sale.getDiscount().signum() > 0) {
                total(totaux, "Remise", "-" + Format.money(sale.getDiscount()), NORMAL);
            }
            if (s.showVatOnTicket()) {
                total(totaux, "TVA (incluse)", Format.money(sale.getTax()), NORMAL);
            }
            total(totaux, "TOTAL", Format.money(sale.getTotal()), NORMAL_B);
            total(totaux, "Payé", Format.money(sale.getPaidAmount()), NORMAL);
            if (sale.getDueAmount().signum() > 0) {
                total(totaux, "Reste dû", Format.money(sale.getDueAmount()), NORMAL_B);
            }
            doc.add(totaux);
            doc.add(espace());
            doc.add(gauche(s.ticketFooter(), PETIT));
        } catch (Exception e) {
            throw new IOException("Génération de la facture impossible : " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        ActivityLogService.get().log(ActivityActions.VENTE, "sales", sale.getId(),
                "Facture générée — " + sale.getNumber());
        return fichier;
    }

    // ------------------------------------------------------------- impression

    /** Imprime (ou ouvre) le PDF via le système. */
    public static void imprimer(Path pdf) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new ValidationException("Impression non disponible sur ce système. "
                        + "PDF enregistré : " + pdf.toAbsolutePath());
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

    // ------------------------------------------------------------- utilitaires

    private static String enteteMagasin(SettingService s) {
        StringBuilder b = new StringBuilder(s.shopName());
        if (!s.shopAddress().isBlank()) {
            b.append('\n').append(s.shopAddress());
        }
        if (!s.shopPhone().isBlank()) {
            b.append("\nTél : ").append(s.shopPhone());
        }
        if (!s.shopEmail().isBlank()) {
            b.append('\n').append(s.shopEmail());
        }
        if (!s.shopTaxId().isBlank()) {
            b.append("\nNIF/RC : ").append(s.shopTaxId());
        }
        return b.toString();
    }

    private static void logo(Document doc, int alignement) {
        SettingService s = SettingService.get();
        if (!s.logoOnDocuments()) {
            return;
        }
        String chemin = s.shopLogo();
        if (chemin == null || chemin.isBlank()) {
            return;
        }
        try {
            Path fichier = Path.of(chemin);
            if (!Files.isRegularFile(fichier)) {
                return;
            }
            Image image = Image.getInstance(fichier.toString());
            image.scaleToFit(140, 48);
            image.setAlignment(alignement);
            doc.add(image);
            doc.add(espace());
        } catch (Exception ignored) {
            // Un logo illisible ne doit jamais empêcher l'édition du document.
        }
    }

    private static Path fichier(String dossier, String nom) throws IOException {
        Path dir = Files.createDirectories(
                Path.of(System.getProperty("user.dir"), "data", dossier));
        return dir.resolve(nom + "-" + System.currentTimeMillis() + ".pdf");
    }

    private static Paragraph centre(String texte, Font police) {
        Paragraph p = new Paragraph(texte, police);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static Paragraph gauche(String texte, Font police) {
        Paragraph p = new Paragraph(texte, police);
        p.setAlignment(Element.ALIGN_LEFT);
        return p;
    }

    private static Paragraph espace() {
        Paragraph p = new Paragraph(" ", PETIT);
        return p;
    }

    private static Paragraph separateur() {
        Paragraph p = new Paragraph("--------------------------------------", PETIT);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static Paragraph ligne(String libelle, String valeur) {
        return gauche(libelle + " : " + valeur, PETIT);
    }

    private static Paragraph ligneValue(String libelle, String valeur) {
        Paragraph p = new Paragraph(libelle + "   " + valeur, PETIT);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Paragraph ligneValueGras(String libelle, String valeur) {
        Paragraph p = new Paragraph(libelle + "   " + valeur, NORMAL_B);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static String ligneTexte(String libelle, String valeur) {
        int largeur = 40;
        int espaces = Math.max(1, largeur - libelle.length() - valeur.length());
        return libelle + " ".repeat(espaces) + valeur + "\n";
    }

    private static String centreTexte(String texte, int largeur) {
        if (texte == null) {
            return "";
        }
        if (texte.length() >= largeur) {
            return texte;
        }
        int gauche = (largeur - texte.length()) / 2;
        return " ".repeat(gauche) + texte;
    }

    private static PdfPCell sansBordure(Phrase contenu) {
        PdfPCell cell = new PdfPCell(contenu);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(2);
        return cell;
    }

    private static PdfPCell info(String libelle, String valeur) {
        PdfPCell cell = new PdfPCell(new Phrase(libelle + " : " + valeur, NORMAL));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingBottom(4);
        return cell;
    }

    private static void entete(PdfPTable table, String texte) {
        PdfPCell cell = new PdfPCell(new Phrase(texte, NORMAL_B));
        cell.setBackgroundColor(new java.awt.Color(0xEE, 0xF2, 0xFF));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static void cellule(PdfPTable table, String texte, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texte == null ? "" : texte, NORMAL));
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private static void total(PdfPTable table, String libelle, String valeur, Font police) {
        PdfPCell c1 = new PdfPCell(new Phrase(libelle, police));
        c1.setBorder(PdfPCell.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell c2 = new PdfPCell(new Phrase(valeur, police));
        c2.setBorder(PdfPCell.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
    }

    /** Arrondi monétaire (utilitaire partagé). */
    public static BigDecimal round(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }
}
