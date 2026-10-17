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
import com.techshop.model.RepairPart;
import com.techshop.model.RepairPayment;
import com.techshop.model.RepairTicket;
import com.techshop.util.Format;
import com.techshop.util.ValidationException;

import java.awt.Color;
import java.awt.Desktop;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Documents du service après-vente (SPEC §4.9) : bon de dépôt remis au client
 * à l'arrivée de l'appareil et bon de retrait à la livraison. Les deux portent
 * un code-barres (ZXing) du numéro de dossier, générés avec OpenPDF.
 */
public final class RepairDocumentService {

    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SOUS_TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font NORMAL_B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font PETIT = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font PETIT_B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

    private static final Color FOND = new Color(0xEE, 0xF2, 0xFF);

    private RepairDocumentService() {
    }

    // ------------------------------------------------------------- bon de dépôt

    /** Génère le bon de dépôt (remis au client) et renvoie le fichier PDF. */
    public static Path bonDepot(RepairTicket t) throws IOException {
        Path fichier = fichier("depots", "depot-" + t.getNumber());
        Document doc = new Document(PageSize.A4, 40, 40, 42, 42);
        try (FileOutputStream flux = new FileOutputStream(fichier.toFile())) {
            PdfWriter.getInstance(doc, flux);
            doc.open();
            entete(doc, "BON DE DÉPÔT — RÉPARATION");
            doc.add(espace());

            PdfPTable infos = new PdfPTable(4);
            infos.setWidthPercentage(100);
            infos.setWidths(new float[]{1.2f, 2, 1.2f, 2});
            info(infos, "N° dossier", t.getNumber());
            info(infos, "Reçu le", Format.dateTime(t.getReceivedAt()));
            info(infos, "Client", t.customerLabel());
            info(infos, "Téléphone", t.getCustomerPhone() == null ? "—" : t.getCustomerPhone());
            info(infos, "Date prévue", Format.date(t.getPromisedDate()));
            info(infos, "Technicien", t.getTechnicianName() == null ? "—" : t.getTechnicianName());
            info(infos, "Statut", t.statusLabel());
            info(infos, "Garantie", t.getWarrantyDays() > 0 ? t.getWarrantyDays() + " jours" : "—");
            doc.add(infos);
            doc.add(espace());

            doc.add(section("Appareil"));
            PdfPTable appareil = new PdfPTable(2);
            appareil.setWidthPercentage(100);
            appareil.setWidths(new float[]{1.6f, 3.4f});
            ligne(appareil, "Type", t.getDeviceType());
            ligne(appareil, "Marque / modèle", t.deviceLabel());
            ligne(appareil, "IMEI / série", t.getSerialOrImei());
            ligne(appareil, "Code de déverrouillage", t.getDevicePassword());
            ligne(appareil, "Accessoires laissés", t.getAccessories());
            ligne(appareil, "État à la réception", t.getPhysicalCondition());
            doc.add(appareil);
            doc.add(espace());

            doc.add(section("Panne déclarée par le client"));
            doc.add(paragraphe(t.getReportedIssue()));
            doc.add(espace());

            PdfPTable montants = new PdfPTable(2);
            montants.setWidthPercentage(60);
            montants.setHorizontalAlignment(Element.ALIGN_LEFT);
            montants.setWidths(new float[]{2, 2});
            total(montants, "Devis estimé", Format.money(t.getQuotedAmount()), NORMAL);
            total(montants, "Acompte versé", Format.money(t.getDepositAmount()), NORMAL_B);
            doc.add(montants);
            doc.add(espace());

            doc.add(separateur());
            doc.add(gauche("Toute intervention supplémentaire fera l'objet d'un accord préalable du client.",
                    PETIT));
            doc.add(gauche("Le présent bon doit être présenté (ou son numéro communiqué) lors du retrait "
                    + "de l'appareil.", PETIT));
            doc.add(espace());
            doc.add(codeBarres(t.getNumber()));
            doc.add(espace());
            doc.add(signatures("Signature du client", "Cachet / signature du magasin"));
        } catch (Exception e) {
            throw new IOException("Génération du bon de dépôt impossible : " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        ActivityLogService.get().log(userId(), ActivityActions.REPARATION, "repair_tickets", t.getId(),
                "Bon de dépôt généré — " + t.getNumber());
        return fichier;
    }

    // ----------------------------------------------------------- bon de retrait

    /** Génère le bon de retrait / facture de réparation et renvoie le PDF. */
    public static Path bonRetrait(RepairTicket t) throws IOException {
        Path fichier = fichier("retraits", "retrait-" + t.getNumber());
        Document doc = new Document(PageSize.A4, 40, 40, 42, 42);
        try (FileOutputStream flux = new FileOutputStream(fichier.toFile())) {
            PdfWriter.getInstance(doc, flux);
            doc.open();
            entete(doc, "BON DE RETRAIT — RÉPARATION");
            doc.add(espace());

            PdfPTable infos = new PdfPTable(4);
            infos.setWidthPercentage(100);
            infos.setWidths(new float[]{1.2f, 2, 1.2f, 2});
            info(infos, "N° dossier", t.getNumber());
            info(infos, "Remis le", Format.dateTime(t.getCompletedAt()));
            info(infos, "Client", t.customerLabel());
            info(infos, "Téléphone", t.getCustomerPhone() == null ? "—" : t.getCustomerPhone());
            info(infos, "Appareil", t.deviceLabel());
            info(infos, "IMEI / série", t.getSerialOrImei());
            info(infos, "Technicien", t.getTechnicianName() == null ? "—" : t.getTechnicianName());
            info(infos, "Garantie jusqu'au", Format.date(t.warrantyEnd()));
            doc.add(infos);
            doc.add(espace());

            if (t.getDiagnosis() != null && !t.getDiagnosis().isBlank()) {
                doc.add(section("Diagnostic"));
                doc.add(paragraphe(t.getDiagnosis()));
                doc.add(espace());
            }

            if (!t.getParts().isEmpty()) {
                doc.add(section("Pièces remplacées"));
                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{4, 1, 1.6f, 1.6f});
                entete(table, "Désignation");
                entete(table, "Qté");
                entete(table, "P.U.");
                entete(table, "Total");
                for (RepairPart p : t.getParts()) {
                    cellule(table, p.productName() == null ? "Pièce #" + p.productId() : p.productName(),
                            Element.ALIGN_LEFT);
                    cellule(table, String.valueOf(p.quantity()), Element.ALIGN_CENTER);
                    cellule(table, Format.money(p.unitPrice()), Element.ALIGN_RIGHT);
                    cellule(table, Format.money(p.total()), Element.ALIGN_RIGHT);
                }
                doc.add(table);
                doc.add(espace());
            }

            PdfPTable totaux = new PdfPTable(2);
            totaux.setWidthPercentage(50);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totaux.setWidths(new float[]{2, 2});
            total(totaux, "Pièces", Format.money(t.getPartsTotal()), NORMAL);
            total(totaux, "Main d'œuvre", Format.money(t.getLaborCost()), NORMAL);
            if (t.getDiscount().signum() > 0) {
                total(totaux, "Remise", "-" + Format.money(t.getDiscount()), NORMAL);
            }
            total(totaux, "TOTAL", Format.money(t.getTotal()), NORMAL_B);
            total(totaux, "Acompte", Format.money(t.getDepositAmount()), NORMAL);
            total(totaux, "Payé", Format.money(t.getPaidAmount()), NORMAL);
            total(totaux, "Reste dû", Format.money(t.getDueAmount()), NORMAL_B);
            doc.add(totaux);
            doc.add(espace());

            if (!t.getPayments().isEmpty()) {
                doc.add(section("Règlements"));
                PdfPTable paiements = new PdfPTable(3);
                paiements.setWidthPercentage(70);
                paiements.setWidths(new float[]{2, 2, 2});
                entete(paiements, "Date");
                entete(paiements, "Mode");
                entete(paiements, "Montant");
                for (RepairPayment p : t.getPayments()) {
                    cellule(paiements, Format.dateTime(p.paymentDate()), Element.ALIGN_LEFT);
                    cellule(paiements, p.methodLabel(), Element.ALIGN_LEFT);
                    cellule(paiements, Format.money(p.amount()), Element.ALIGN_RIGHT);
                }
                doc.add(paiements);
                doc.add(espace());
            }

            doc.add(separateur());
            doc.add(gauche(t.warrantyEnd() == null
                    ? "Garantie : se référer aux conditions du magasin."
                    : "Appareil garanti jusqu'au " + Format.date(t.warrantyEnd())
                            + " pour la panne traitée.", PETIT));
            doc.add(espace());
            doc.add(codeBarres(t.getNumber()));
            doc.add(espace());
            doc.add(signatures("Signature du client", "Cachet / signature du magasin"));
        } catch (Exception e) {
            throw new IOException("Génération du bon de retrait impossible : " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        ActivityLogService.get().log(userId(), ActivityActions.REPARATION, "repair_tickets", t.getId(),
                "Bon de retrait généré — " + t.getNumber());
        return fichier;
    }

    // ----------------------------------------------------------------- facture

    /**
     * Facture de la réparation (SPEC §4.9), remise au client à la livraison :
     * détail des pièces et de la main d'œuvre, totaux et règlements.
     */
    public static Path facture(RepairTicket t) throws IOException {
        Path fichier = fichier("factures", "facture-" + t.getNumber());
        Document doc = new Document(PageSize.A4, 40, 40, 42, 42);
        try (FileOutputStream flux = new FileOutputStream(fichier.toFile())) {
            PdfWriter.getInstance(doc, flux);
            doc.open();
            entete(doc, "FACTURE — RÉPARATION");
            doc.add(espace());

            PdfPTable infos = new PdfPTable(4);
            infos.setWidthPercentage(100);
            infos.setWidths(new float[]{1.2f, 2, 1.2f, 2});
            info(infos, "N° facture", "F-" + t.getNumber());
            info(infos, "Date", Format.dateTime(t.getCompletedAt() == null
                    ? LocalDateTime.now() : t.getCompletedAt()));
            info(infos, "Client", t.customerLabel());
            info(infos, "Téléphone", t.getCustomerPhone());
            info(infos, "Appareil", t.deviceLabel());
            info(infos, "IMEI / série", t.getSerialOrImei());
            info(infos, "Dossier SAV", t.getNumber());
            info(infos, "Technicien", t.getTechnicianName());
            doc.add(infos);
            doc.add(espace());

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 1, 1.6f, 1.6f});
            entete(table, "Désignation");
            entete(table, "Qté");
            entete(table, "P.U.");
            entete(table, "Total");
            for (RepairPart p : t.getParts()) {
                cellule(table, p.productName() == null ? "Pièce #" + p.productId() : p.productName(),
                        Element.ALIGN_LEFT);
                cellule(table, String.valueOf(p.quantity()), Element.ALIGN_CENTER);
                cellule(table, Format.money(p.unitPrice()), Element.ALIGN_RIGHT);
                cellule(table, Format.money(p.total()), Element.ALIGN_RIGHT);
            }
            if (t.getLaborCost().signum() > 0) {
                cellule(table, "Main d'œuvre (réparation)", Element.ALIGN_LEFT);
                cellule(table, "1", Element.ALIGN_CENTER);
                cellule(table, Format.money(t.getLaborCost()), Element.ALIGN_RIGHT);
                cellule(table, Format.money(t.getLaborCost()), Element.ALIGN_RIGHT);
            }
            doc.add(table);
            doc.add(espace());

            PdfPTable totaux = new PdfPTable(2);
            totaux.setWidthPercentage(50);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totaux.setWidths(new float[]{2, 2});
            total(totaux, "Pièces", Format.money(t.getPartsTotal()), NORMAL);
            total(totaux, "Main d'œuvre", Format.money(t.getLaborCost()), NORMAL);
            if (t.getDiscount().signum() > 0) {
                total(totaux, "Remise", "-" + Format.money(t.getDiscount()), NORMAL);
            }
            total(totaux, "TOTAL TTC", Format.money(t.getTotal()), NORMAL_B);
            total(totaux, "Payé", Format.money(t.getPaidAmount()), NORMAL);
            total(totaux, "Reste dû", Format.money(t.getDueAmount()), NORMAL_B);
            doc.add(totaux);
            doc.add(espace());

            if (!t.getPayments().isEmpty()) {
                doc.add(section("Règlements"));
                PdfPTable paiements = new PdfPTable(3);
                paiements.setWidthPercentage(70);
                paiements.setWidths(new float[]{2, 2, 2});
                entete(paiements, "Date");
                entete(paiements, "Mode");
                entete(paiements, "Montant");
                for (RepairPayment p : t.getPayments()) {
                    cellule(paiements, Format.dateTime(p.paymentDate()), Element.ALIGN_LEFT);
                    cellule(paiements, p.methodLabel(), Element.ALIGN_LEFT);
                    cellule(paiements, Format.money(p.amount()), Element.ALIGN_RIGHT);
                }
                doc.add(paiements);
                doc.add(espace());
            }

            doc.add(separateur());
            doc.add(gauche("Garantie sur la panne traitée : "
                    + (t.warrantyEnd() == null ? "selon les conditions du magasin"
                            : "jusqu'au " + Format.date(t.warrantyEnd())) + ".", PETIT));
            doc.add(espace());
            doc.add(codeBarres(t.getNumber()));
        } catch (Exception e) {
            throw new IOException("Génération de la facture impossible : " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        ActivityLogService.get().log(userId(), ActivityActions.REPARATION, "repair_tickets", t.getId(),
                "Facture générée — " + t.getNumber());
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

    private static void entete(Document doc, String titre) throws Exception {
        SettingService s = SettingService.get();
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 2});
        PdfPCell gauche = sansBordure(new Phrase(s.shopName() + "\n" + s.shopAddress()
                + (s.shopPhone().isBlank() ? "" : "\nTél : " + s.shopPhone()), PETIT));
        PdfPCell droite = sansBordure(new Phrase(titre, TITRE));
        droite.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(gauche);
        table.addCell(droite);
        doc.add(table);
    }

    private static Paragraph section(String texte) {
        return gauche(texte.toUpperCase(), SOUS_TITRE);
    }

    private static void ligne(PdfPTable table, String libelle, String valeur) {
        PdfPCell c1 = new PdfPCell(new Phrase(libelle, PETIT_B));
        c1.setBorder(PdfPCell.NO_BORDER);
        c1.setPadding(3);
        PdfPCell c2 = new PdfPCell(new Phrase(valeur == null || valeur.isBlank() ? "—" : valeur, NORMAL));
        c2.setBorder(PdfPCell.NO_BORDER);
        c2.setPadding(3);
        table.addCell(c1);
        table.addCell(c2);
    }

    private static void info(PdfPTable table, String libelle, String valeur) {
        PdfPCell cell = new PdfPCell(new Phrase(libelle + " : "
                + (valeur == null || valeur.isBlank() ? "—" : valeur), NORMAL));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingBottom(5);
        table.addCell(cell);
    }

    private static void entete(PdfPTable table, String texte) {
        PdfPCell cell = new PdfPCell(new Phrase(texte, PETIT_B));
        cell.setBackgroundColor(FOND);
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
        c1.setPadding(3);
        PdfPCell c2 = new PdfPCell(new Phrase(valeur, police));
        c2.setBorder(PdfPCell.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(3);
        table.addCell(c1);
        table.addCell(c2);
    }

    private static Paragraph paragraphe(String texte) {
        Paragraph p = new Paragraph(texte == null || texte.isBlank() ? "—" : texte, NORMAL);
        p.setAlignment(Element.ALIGN_LEFT);
        p.setLeading(14);
        return p;
    }

    private static PdfPTable codeBarres(String numero) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(45);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        try {
            byte[] png = BarcodeService.toPng(numero, 300, 70);
            Image image = Image.getInstance(png);
            image.scaleToFit(200, 55);
            PdfPCell cell = new PdfPCell(image, false);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        } catch (Exception e) {
            table.addCell(sansBordure(new Phrase(numero, NORMAL_B)));
        }
        PdfPCell texte = new PdfPCell(new Phrase(numero, PETIT));
        texte.setBorder(PdfPCell.NO_BORDER);
        texte.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(texte);
        return table;
    }

    private static PdfPTable signatures(String g, String d) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1});
        table.addCell(cadreSignature(g));
        table.addCell(cadreSignature(d));
        return table;
    }

    private static PdfPCell cadreSignature(String libelle) {
        PdfPCell cell = new PdfPCell(new Phrase("\n\n\n" + libelle, PETIT));
        cell.setPadding(6);
        cell.setMinimumHeight(60);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private static PdfPCell sansBordure(Phrase contenu) {
        PdfPCell cell = new PdfPCell(contenu);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(2);
        return cell;
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
        return new Paragraph(" ", PETIT);
    }

    private static Paragraph separateur() {
        Paragraph p = new Paragraph("--------------------------------------------------------------------", PETIT);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static Path fichier(String dossier, String nom) throws IOException {
        Path dir = Files.createDirectories(
                Path.of(System.getProperty("user.dir"), "data", dossier));
        return dir.resolve(nom + "-" + System.currentTimeMillis() + ".pdf");
    }

    private static int userId() {
        var u = SessionContext.get().getUser();
        return u == null ? 1 : u.getId();
    }
}
