package com.ferreteria.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos necesarios para imprimir un ticket de venta.
 * Se arma igual desde una venta recién registrada o desde el historial,
 * para que ambos caminos impriman exactamente lo mismo.
 */
public class TicketData {

    /** Una línea de producto del ticket. */
    public static class Line {
        private final String code;
        private final String name;
        private final double quantity;
        private final double unitPrice;
        private final double subtotal;

        public Line(String code, String name, double quantity, double unitPrice, double subtotal) {
            this.code = code;
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.subtotal = subtotal;
        }

        public String getCode()      { return code; }
        public String getName()      { return name; }
        public double getQuantity()  { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getSubtotal()  { return subtotal; }
    }

    private AppSettings settings;
    private Integer saleId;
    private LocalDateTime dateTime = LocalDateTime.now();
    private String paymentMethod;
    private String customerName;
    private double total;
    private final List<Line> lines = new ArrayList<>();

    public AppSettings getSettings()             { return settings; }
    public void setSettings(AppSettings v)       { this.settings = v; }

    public Integer getSaleId()                   { return saleId; }
    public void setSaleId(Integer v)             { this.saleId = v; }

    public LocalDateTime getDateTime()           { return dateTime; }
    public void setDateTime(LocalDateTime v)     { this.dateTime = v; }

    public String getPaymentMethod()             { return paymentMethod; }
    public void setPaymentMethod(String v)       { this.paymentMethod = v; }

    public String getCustomerName()              { return customerName; }
    public void setCustomerName(String v)        { this.customerName = v; }

    public double getTotal()                     { return total; }
    public void setTotal(double v)               { this.total = v; }

    public List<Line> getLines()                 { return lines; }

    public void addLine(Line line) {
        if (line != null) lines.add(line);
    }
}
