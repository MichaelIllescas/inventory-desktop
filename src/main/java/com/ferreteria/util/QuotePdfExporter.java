package com.ferreteria.util;

import com.ferreteria.models.AppSettings;
import com.ferreteria.models.Customer;
import com.ferreteria.models.Quote;
import com.ferreteria.models.QuoteLineItem;
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

/**
 * Exporta un presupuesto a PDF con el mismo formato que el estado de cuenta corriente:
 * logo y datos del negocio arriba, datos del cliente, total destacado y detalle de items.
 */
public final class QuotePdfExporter {

    private QuotePdfExporter() {}

    public static void export(Path output, Quote quote, Customer customer, AppSettings settings) {
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
            Font emisionFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(148, 163, 184));

            // -- Fila 1: logo/nombre del negocio (izq) + datos de contacto (der) --
            boolean useBusiness = settings != null && settings.hasData();
            PdfPTable headingTable = new PdfPTable(new float[]{1f, 1f});
            headingTable.setWidthPercentage(100);
            PdfPCell left = cellNoBorder();

            boolean customLogoRendered = false;
            if (useBusiness && settings.hasLogo()) {
                Image bizLogo = loadLogoFromFile(settings.getLogoPath());
                if (bizLogo != null) {
                    bizLogo.scaleToFit(190, 88);
                    bizLogo.setAlignment(Element.ALIGN_LEFT);
                    left.addElement(bizLogo);
                    customLogoRendered = true;
                }
            }
            if (!customLogoRendered) {
                Image fallbackLogo = loadLogo();
                if (fallbackLogo != null) {
                    fallbackLogo.scaleToFit(190, 88);
                    fallbackLogo.setAlignment(Element.ALIGN_LEFT);
                    left.addElement(fallbackLogo);
                } else {
                    left.addElement(new Paragraph("ImperialNet", subtitle));
                }
            }

            PdfPCell right = cellNoBorder();
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            right.setVerticalAlignment(Element.ALIGN_MIDDLE);
            if (useBusiness) {
                if (safe(settings.getBusinessName()).length() > 1)
                    right.addElement(rightAligned(settings.getBusinessName(), title));
                if (safe(settings.getBusinessAddress()).length() > 1)
                    right.addElement(rightAligned(settings.getBusinessAddress(), subtitle));
                if (safe(settings.getBusinessPhone()).length() > 1)
                    right.addElement(rightAligned("Tel: " + settings.getBusinessPhone(), subtitle));
                if (safe(settings.getBusinessCuit()).length() > 1)
                    right.addElement(rightAligned("CUIT: " + settings.getBusinessCuit(), subtitle));
            }
            headingTable.addCell(left);
            headingTable.addCell(right);
            document.add(headingTable);

            document.add(space(6));

            // -- Fila 2: titulo del documento (izq) + fecha de emision (der) --
            PdfPTable titleRow = new PdfPTable(new float[]{1f, 1f});
            titleRow.setWidthPercentage(100);
            PdfPCell titleCell = cellNoBorder();
            titleCell.addElement(new Paragraph("Presupuesto", title));
            titleCell.addElement(new Paragraph("N° " + (quote.getId() == null ? "-" : quote.getId()), subtitle));
            PdfPCell dateCell = cellNoBorder();
            dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            dateCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
            LocalDateTime issued = parseDateTime(quote.getDate());
            String fechaHora = issued.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String comprobante = issued.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            dateCell.addElement(rightAligned("Emitido: " + fechaHora + "  ·  Comp.: " + comprobante, emisionFont));
            titleRow.addCell(titleCell);
            titleRow.addCell(dateCell);
            document.add(titleRow);

            document.add(space(8));

            LocalDate validUntil = issued.toLocalDate().plusDays(Math.max(quote.getValidDays(), 0));
            String customerName = customer != null && customer.getName() != null
                    ? customer.getName()
                    : quote.getCustomerName();
            PdfPTable customerTable = new PdfPTable(new float[]{1.2f, 2.8f, 1.1f, 2.9f});
            customerTable.setWidthPercentage(100);
            String taxId = customer == null ? null : customer.getTaxId();
            String validText = validUntil.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " (" + quote.getValidDays() + " días)";

            addInfoCell(customerTable, "Cliente", safe(customerName), sectionHeader, body);
            addInfoCell(customerTable, "Teléfono",
                    safe(customer == null ? null : customer.getPhone()), sectionHeader, body);
            addInfoCell(customerTable, "Dirección",
                    safe(customer == null ? null : customer.getAddress()), sectionHeader, body);
            // El DNI/CUIT es opcional: si el cliente no lo tiene cargado, no se muestra la fila.
            if (taxId != null && !taxId.isBlank()) {
                addInfoCell(customerTable, "DNI / CUIT", taxId, sectionHeader, body);
                addInfoCell(customerTable, "Válido hasta", validText, sectionHeader, body);
                PdfPCell filler = new PdfPCell(new Phrase("", body));
                filler.setColspan(2);
                filler.setBorderColor(new Color(226, 232, 240));
                filler.setPadding(8);
                customerTable.addCell(filler);
            } else {
                addInfoCell(customerTable, "Válido hasta", validText, sectionHeader, body);
            }
            document.add(customerTable);

            document.add(space(8));

            PdfPTable itemsTable = new PdfPTable(new float[]{0.6f, 1.2f, 3.4f, 1f, 1.3f, 1.4f});
            itemsTable.setWidthPercentage(100);
            addHeader(itemsTable, "N°", header, Element.ALIGN_CENTER);
            addHeader(itemsTable, "Código", header, Element.ALIGN_CENTER);
            addHeader(itemsTable, "Descripción", header, Element.ALIGN_CENTER);
            addHeader(itemsTable, "Cant.", header, Element.ALIGN_CENTER);
            addHeader(itemsTable, "P. unitario", header, Element.ALIGN_CENTER);
            addHeader(itemsTable, "Subtotal", header, Element.ALIGN_CENTER);

            List<QuoteLineItem> items = quote.getItems();
            if (items.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("El presupuesto no tiene items.", body));
                empty.setColspan(6);
                empty.setPadding(10);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setBorderColor(new Color(226, 232, 240));
                itemsTable.addCell(empty);
            }

            Font discountBody = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(190, 18, 60));
            int rowIndex = 0;
            for (QuoteLineItem item : items) {
                Color rowColor = (rowIndex % 2 == 0) ? Color.WHITE : new Color(248, 250, 252);
                // Un descuento es un item con importe negativo: se resalta en rojo.
                Font rowFont = item.getSubtotal() < 0 ? discountBody : body;
                addBody(itemsTable, String.valueOf(++rowIndex), rowFont, Element.ALIGN_CENTER, rowColor);
                addBody(itemsTable, safe(item.getCode()), rowFont, Element.ALIGN_CENTER, rowColor);
                addBody(itemsTable, safe(item.getDescription()), rowFont, Element.ALIGN_LEFT, rowColor);
                addBody(itemsTable, quantity(item.getQuantity()), rowFont, Element.ALIGN_CENTER, rowColor);
                addBody(itemsTable, money(item.getPrice()), rowFont, Element.ALIGN_RIGHT, rowColor);
                addBody(itemsTable, money(item.getSubtotal()), rowFont, Element.ALIGN_RIGHT, rowColor);
            }
            document.add(itemsTable);

            document.add(space(6));

            Color totalBorderColor = new Color(191, 219, 254);
            Color totalBgColor = new Color(239, 246, 255);
            PdfPTable totalTable = new PdfPTable(new float[]{1f, 1f});
            totalTable.setWidthPercentage(100);
            PdfPCell totalTitle = new PdfPCell(new Phrase("TOTAL PRESUPUESTADO", totalLabel));
            totalTitle.setBorder(Rectangle.BOX);
            totalTitle.setBorderColor(totalBorderColor);
            totalTitle.setBackgroundColor(totalBgColor);
            totalTitle.setPadding(10);
            PdfPCell totalAmount = new PdfPCell(new Phrase(money(quote.getTotal()), totalValue));
            totalAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalAmount.setBorder(Rectangle.BOX);
            totalAmount.setBorderColor(totalBorderColor);
            totalAmount.setBackgroundColor(totalBgColor);
            totalAmount.setPadding(10);
            totalTable.addCell(totalTitle);
            totalTable.addCell(totalAmount);
            document.add(totalTable);

            if (quote.getNotes() != null && !quote.getNotes().isBlank()) {
                document.add(space(8));
                PdfPTable notesTable = new PdfPTable(1);
                notesTable.setWidthPercentage(100);
                PdfPCell notesCell = new PdfPCell();
                notesCell.setBackgroundColor(new Color(248, 250, 252));
                notesCell.setBorderColor(new Color(226, 232, 240));
                notesCell.setPadding(8);
                notesCell.addElement(new Paragraph("Observaciones", sectionHeader));
                notesCell.addElement(new Paragraph(quote.getNotes(), body));
                notesTable.addCell(notesCell);
                document.add(notesTable);
            }

            document.add(space(8));
            document.add(new Paragraph(
                    "Presupuesto sin valor fiscal. Precios sujetos a modificación pasada la fecha de validez. "
                            + "Emitido por Sistema de Inventario.", subtitle));
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

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private static Image loadLogo() {
        try (InputStream input = QuotePdfExporter.class.getResourceAsStream("/images/logo_imperial_net.png")) {
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

    private static Image loadLogoFromFile(String path) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
            return Image.getInstance(bytes);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String quantity(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.2f", value).replace('.', ',');
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
