package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.AppSettings;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SaleLineItem;
import com.ferreteria.models.TicketData;
import com.ferreteria.util.AppLogger;
import com.ferreteria.util.TicketPrinter;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma e imprime los tickets de venta.
 *
 * Centraliza el armado del {@link TicketData} para que la venta recién
 * registrada y la reimpresión desde el historial produzcan el mismo ticket.
 */
public class TicketService {

    private static final DateTimeFormatter DB_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppSettingsService settingsService;

    public TicketService() {
        this(new AppSettingsService());
    }

    public TicketService(AppSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** Ticket de una venta recién registrada, con los ítems que están en pantalla. */
    public TicketData fromNewSale(int saleId, List<SaleLineItem> lines, double total,
                                  String paymentMethod, String customerName) {
        TicketData data = baseTicket();
        data.setSaleId(saleId);
        data.setDateTime(LocalDateTime.now());
        data.setPaymentMethod(paymentMethod);
        data.setCustomerName(customerName);
        data.setTotal(total);
        for (SaleLineItem line : lines) {
            data.addLine(new TicketData.Line(
                    line.getProductCode(),
                    line.getProductName(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getSubtotal()));
        }
        return data;
    }

    /**
     * Nombre del cliente asociado a una venta, o null si fue al contado.
     * Solo las ventas en cuenta corriente guardan cliente.
     */
    public String findCustomerName(int saleId) {
        String sql = "SELECT c.name FROM sales s JOIN customers c ON c.id = s.customer_id WHERE s.id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            AppLogger.warn("TicketService", "findCustomerName",
                    "No se pudo obtener el cliente de la venta " + saleId);
            return null;
        }
    }

    /** Ticket reconstruido desde el historial, a partir de las filas de detalle. */
    public TicketData fromHistory(List<SaleDetailRow> rows, String customerName) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene items para imprimir.");
        }
        SaleDetailRow first = rows.get(0);
        TicketData data = baseTicket();
        data.setSaleId(first.getSaleId());
        data.setDateTime(parseDate(first.getSaleDate()));
        data.setPaymentMethod(first.getPaymentMethod());
        data.setCustomerName(customerName);
        data.setTotal(first.getSaleTotal());
        for (SaleDetailRow row : rows) {
            data.addLine(new TicketData.Line(
                    row.getProductCode(),
                    row.getProductName(),
                    row.getQuantity(),
                    row.getUnitPrice(),
                    row.getSubtotal()));
        }
        return data;
    }

    /** Manda el ticket a la impresora configurada, sin diálogo. */
    public void print(TicketData data) {
        AppSettings settings = data.getSettings();
        String printer = settings == null ? null : settings.getTicketPrinter();
        double width = settings == null ? 80 : settings.getTicketPaperWidthMm();
        AppLogger.info("TicketService", "print",
                "Imprimiendo ticket venta=" + data.getSaleId()
                        + " impresora=" + (printer == null || printer.isBlank() ? "(predeterminada)" : printer)
                        + " ancho=" + width + "mm");
        TicketPrinter.print(data, width, printer);
    }

    /** Genera el ticket como PDF, para vista previa o para guardarlo. */
    public void exportPdf(TicketData data, Path output) {
        AppSettings settings = data.getSettings();
        double width = settings == null ? 80 : settings.getTicketPaperWidthMm();
        TicketPrinter.exportPdf(data, width, output);
    }

    private TicketData baseTicket() {
        TicketData data = new TicketData();
        data.setSettings(settingsService.load());
        return data;
    }

    private LocalDateTime parseDate(String value) {
        try {
            return LocalDateTime.parse(value.trim(), DB_DATE);
        } catch (Exception e) {
            // Formatos viejos o parciales: preferimos imprimir con la fecha de hoy
            // antes que romper la reimpresión.
            AppLogger.warn("TicketService", "parseDate", "Fecha no parseable: " + value);
            return LocalDateTime.now();
        }
    }
}
