package com.ferreteria.util;

import com.ferreteria.models.Customer;
import com.ferreteria.models.CustomerDebtRow;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class CurrentAccountPdfExporter {

    private CurrentAccountPdfExporter() {}

    public static void export(
            Path output,
            Customer customer,
            double balance,
            List<CustomerDebtRow> debts,
            Map<Integer, String> saleDetailsBySaleId
    ) {
        try {
            Document document = new Document(PageSize.A4, 32, 32, 28, 28);
            PdfWriter.getInstance(document, new FileOutputStream(output.toFile()));
            document.open();

            Font title = new Font(Font.HELVETICA, 17, Font.BOLD, new Color(15, 23, 42));
            Font subtitle = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(71, 85, 105));
            Font sectionHeader = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(30, 41, 59));
            Font header = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            Font body = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(51, 65, 85));
            Font totalLabel = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(30, 41, 59));
            Font totalValue = new Font(Font.HELVETICA, 15, Font.BOLD, new Color(37, 99, 235));

            List<CustomerDebtRow> pendingDebts = debts.stream()
                    .filter(d -> d.getPendingAmount() > 0.000001d)
                    .toList();
            double pendingSalesDebt = pendingDebts.stream().mapToDouble(CustomerDebtRow::getPendingAmount).sum();
            double manualAdjustmentsDebt = balance - pendingSalesDebt;

            PdfPTable headingTable = new PdfPTable(new float[]{1f, 1f});
            headingTable.setWidthPercentage(100);
            PdfPCell left = cellNoBorder();
            Image logo = loadLogo();
            if (logo != null) {
                logo.scaleToFit(130, 42);
                logo.setAlignment(Element.ALIGN_LEFT);
                left.addElement(logo);
            } else {
                left.addElement(new Paragraph("ImperialNet", subtitle));
            }
            Paragraph heading = new Paragraph("Estado de Deuda", title);
            heading.setSpacingBefore(2);
            left.addElement(heading);
            left.addElement(new Paragraph("Cuenta corriente de cliente", subtitle));
            PdfPCell right = cellNoBorder();
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            right.addElement(rightAligned("Fecha emision: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subtitle));
            right.addElement(rightAligned("Hora: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), subtitle));
            right.addElement(rightAligned("Comprobante: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")), subtitle));
            headingTable.addCell(left);
            headingTable.addCell(right);
            document.add(headingTable);

            document.add(space(8));

            PdfPTable customerTable = new PdfPTable(new float[]{1.2f, 2.8f, 1.1f, 2.9f});
            customerTable.setWidthPercentage(100);
            addInfoCell(customerTable, "Cliente", safe(customer.getName()), sectionHeader, body);
            addInfoCell(customerTable, "Telefono", safe(customer.getPhone()), sectionHeader, body);
            addInfoCell(customerTable, "Direccion", safe(customer.getAddress()), sectionHeader, body);
            addInfoCell(customerTable, "Compras con deuda", String.valueOf(pendingDebts.size()), sectionHeader, body);
            document.add(customerTable);

            document.add(space(8));

            PdfPTable totalTable = new PdfPTable(new float[]{1f, 1f});
            totalTable.setWidthPercentage(100);
            PdfPCell totalTitle = new PdfPCell(new Phrase("TOTAL ADEUDADO", totalLabel));
            totalTitle.setBorder(Rectangle.BOX);
            totalTitle.setBorderColor(new Color(191, 219, 254));
            totalTitle.setBackgroundColor(new Color(239, 246, 255));
            totalTitle.setPadding(10);
            PdfPCell totalAmount = new PdfPCell(new Phrase(money(balance), totalValue));
            totalAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalAmount.setBorder(Rectangle.BOX);
            totalAmount.setBorderColor(new Color(191, 219, 254));
            totalAmount.setBackgroundColor(new Color(239, 246, 255));
            totalAmount.setPadding(10);
            totalTable.addCell(totalTitle);
            totalTable.addCell(totalAmount);
            document.add(totalTable);

            document.add(space(4));
            PdfPTable breakdownTable = new PdfPTable(new float[]{1f, 1f});
            breakdownTable.setWidthPercentage(100);
            addBody(breakdownTable, "Deuda por ventas CC", sectionHeader, Element.ALIGN_LEFT, Color.WHITE);
            addBody(breakdownTable, money(pendingSalesDebt), body, Element.ALIGN_RIGHT, Color.WHITE);
            addBody(breakdownTable, "Deuda anterior / ajustes", sectionHeader, Element.ALIGN_LEFT, new Color(248, 250, 252));
            addBody(breakdownTable, money(manualAdjustmentsDebt), body, Element.ALIGN_RIGHT, new Color(248, 250, 252));
            document.add(breakdownTable);

            document.add(space(10));

            PdfPTable debtTable = new PdfPTable(new float[]{0.8f, 1.5f, 2.9f, 1.2f, 1.2f, 1.2f, 1f});
            debtTable.setWidthPercentage(100);
            addHeader(debtTable, "Venta", header, Element.ALIGN_CENTER);
            addHeader(debtTable, "Fecha", header, Element.ALIGN_CENTER);
            addHeader(debtTable, "Detalle", header, Element.ALIGN_LEFT);
            addHeader(debtTable, "Total", header, Element.ALIGN_RIGHT);
            addHeader(debtTable, "Pagado", header, Element.ALIGN_RIGHT);
            addHeader(debtTable, "Pendiente", header, Element.ALIGN_RIGHT);
            addHeader(debtTable, "Estado", header, Element.ALIGN_CENTER);

            if (pendingDebts.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No hay compras con deuda pendiente.", body));
                empty.setColspan(7);
                empty.setPadding(10);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setBorderColor(new Color(226, 232, 240));
                debtTable.addCell(empty);
            }

            int rowIndex = 0;
            for (CustomerDebtRow row : pendingDebts) {
                Color rowColor = (rowIndex++ % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                addBody(debtTable, String.valueOf(row.getSaleId()), body, Element.ALIGN_CENTER, rowColor);
                addBody(debtTable, normalizeDate(row.getSaleDate()), body, Element.ALIGN_CENTER, rowColor);
                String details = saleDetailsBySaleId == null ? null : saleDetailsBySaleId.get(row.getSaleId());
                addBody(debtTable, safe(details), body, Element.ALIGN_LEFT, rowColor);
                addBody(debtTable, money(row.getSaleTotal()), body, Element.ALIGN_RIGHT, rowColor);
                addBody(debtTable, money(row.getAppliedAmount()), body, Element.ALIGN_RIGHT, rowColor);
                addBody(debtTable, money(row.getPendingAmount()), body, Element.ALIGN_RIGHT, rowColor);
                addBody(debtTable, row.getStatus(), body, Element.ALIGN_CENTER, rowColor);
            }
            document.add(debtTable);
            document.add(space(8));
            document.add(new Paragraph("Documento informativo. Emitido por Sistema de Inventario.", subtitle));
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo exportar PDF: " + e.getMessage(), e);
        }
    }

    private static void addHeader(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(new Color(37, 99, 235));
        cell.setBorderColor(new Color(29, 78, 216));
        cell.setPadding(8);
        table.addCell(cell);
    }

    private static void addBody(PdfPTable table, String text, Font font, int alignment, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(7);
        table.addCell(cell);
    }

    private static void addInfoCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(new Color(248, 250, 252));
        labelCell.setBorderColor(new Color(226, 232, 240));
        labelCell.setPadding(8);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorderColor(new Color(226, 232, 240));
        valueCell.setPadding(8);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static PdfPCell cellNoBorder() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static Paragraph rightAligned(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Paragraph space(int spacingAfter) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static Image loadLogo() {
        try (InputStream input = CurrentAccountPdfExporter.class.getResourceAsStream("/images/logo_imperial_net.png")) {
            if (input == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return Image.getInstance(buffer.toByteArray());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String money(double total) {
        String num = String.format("%.2f", total).replace('.', ',');
        int i = num.indexOf(',');
        if (i > 3) {
            StringBuilder sb = new StringBuilder(num);
            for (int j = i - 3; j > 0; j -= 3) sb.insert(j, '.');
            num = sb.toString();
        }
        return "$ " + num;
    }
}
