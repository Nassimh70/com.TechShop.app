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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ExportUtil {
    private ExportUtil() {}

    public static File exportPdf(TableView<?> table, String filename) throws Exception {
        File f = new File(System.getProperty("user.home"), "Documents/" + filename);
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, new FileOutputStream(f));
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
                Object v = col.getCellObservableValue(item).getValue();
                pdfTable.addCell(v == null ? "" : format(v));
            }
        }
        doc.add(pdfTable);
        doc.close();
        return f;
    }

    public static File exportExcel(TableView<?> table, String filename) throws Exception {
        File f = new File(System.getProperty("user.home"), "Documents/" + filename);
        Workbook wb = new XSSFWorkbook();
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
                Object v = col.getCellObservableValue(item).getValue();
                org.apache.poi.ss.usermodel.Cell c = row.createCell(cc++);
                writeCell(c, v);
            }
        }
        for (int i = 0; i < table.getColumns().size(); i++) sheet.autoSizeColumn(i);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            wb.write(fos);
        }
        wb.close();
        return f;
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