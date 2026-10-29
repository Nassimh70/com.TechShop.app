package com.techshop.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ExportUtil {
    private ExportUtil() {}

    public static File exportPdf(TableView<?> table, String filename) throws Exception {
        File f = outputFile(filename);
        Document doc = new Document(PageSize.A4.rotate());
        try (FileOutputStream output = new FileOutputStream(f)) {
            PdfWriter.getInstance(doc, output);
            doc.open();
            com.lowagie.text.Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            doc.add(new Paragraph("TechShop Manager - Rapport", title));
            doc.add(new Paragraph("Généré le : " + Format.dateTime(LocalDateTime.now())));
            doc.add(Chunk.NEWLINE);

            PdfPTable pdfTable = new PdfPTable(Math.max(1, table.getColumns().size()));
            pdfTable.setWidthPercentage(100);
            for (TableColumn<?, ?> col : table.getColumns()) {
                PdfPCell c = new PdfPCell(new Phrase(col.getText()));
                c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                pdfTable.addCell(c);
            }
            @SuppressWarnings("unchecked")
            TableView<Object> tv = (TableView<Object>) table;
            for (Object item : tv.getItems()) {
                for (TableColumn<Object, ?> col : (java.util.List<TableColumn<Object, ?>>) (Object) tv.getColumns()) {
                    var observable = col.getCellObservableValue(item);
                    Object v = observable == null ? null : observable.getValue();
                    pdfTable.addCell(v == null ? "" : format(v));
                }
            }
            doc.add(pdfTable);
            // OpenPDF doit fermer le document avant le flux pour écrire la
            // table xref et terminer correctement le fichier PDF.
            doc.close();
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        if (f.length() == 0) {
            throw new IOException("Le fichier PDF est vide.");
        }
        return f;
    }

    public static File exportExcel(TableView<?> table, String filename) throws Exception {
        File f = outputFile(filename);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Rapport");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            int hc = 0;
            for (TableColumn<?, ?> col : table.getColumns()) {
                org.apache.poi.ss.usermodel.Cell c = header.createCell(hc++);
                c.setCellValue(col.getText());
            }
            int r = 1;
            @SuppressWarnings("unchecked")
            TableView<Object> tv2 = (TableView<Object>) table;
            for (Object item : tv2.getItems()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                int cc = 0;
                for (TableColumn<Object, ?> col : (java.util.List<TableColumn<Object, ?>>) (Object) tv2.getColumns()) {
                    var observable = col.getCellObservableValue(item);
                    Object v = observable == null ? null : observable.getValue();
                    org.apache.poi.ss.usermodel.Cell c = row.createCell(cc++);
                    writeCell(c, v);
                }
            }
            for (int i = 0; i < table.getColumns().size(); i++) sheet.autoSizeColumn(i);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                wb.write(fos);
            }
        }
        return f;
    }

    /** Génère puis envoie le rapport PDF à l'imprimante système. */
    public static File print(TableView<?> table, String filename) throws Exception {
        File pdf = exportPdf(table, filename);
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
            throw new IOException("Aucune imprimante système disponible. PDF enregistré : "
                    + pdf.getAbsolutePath());
        }
        Desktop.getDesktop().print(pdf);
        return pdf;
    }

    private static File outputFile(String filename) throws IOException {
        Path documents = Path.of(System.getProperty("user.home"), "Documents");
        Files.createDirectories(documents);
        return documents.resolve(filename).toFile();
    }

    private static String format(Object v) {
        if (v instanceof BigDecimal bd) return Format.money(bd);
        if (v instanceof LocalDate d) return Format.date(d);
        if (v instanceof LocalDateTime dt) return Format.dateTime(dt);
        return v == null ? "" : v.toString();
    }

    private static void writeCell(org.apache.poi.ss.usermodel.Cell c, Object v) {
        if (v == null) return;
        if (v instanceof Number n) {
            c.setCellType(CellType.NUMERIC);
            if (n instanceof BigDecimal bd) c.setCellValue(bd.doubleValue());
            else c.setCellValue(n.doubleValue());
        } else if (v instanceof LocalDate ld) {
            c.setCellValue(ld);
        } else {
            c.setCellType(CellType.STRING);
            c.setCellValue(v.toString());
        }
    }
}