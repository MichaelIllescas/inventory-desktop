package com.ferreteria.models;

/**
 * Fila de reporte: detalle de una línea de venta (ítem dentro de una venta).
 */
public class SaleDetailRow {

    private int saleId;
    private int productId;
    private String saleDate;
    private String productCode;
    private String productName;
    private double quantity;
    private double unitPrice;
    private double subtotal;
    private double saleTotal;
    private String paymentMethod;
    /** Nombre del cliente de la venta; null o vacio si fue a consumidor final. */
    private String customerName;

    public SaleDetailRow(int saleId, String saleDate, String productCode, String productName,
                         double quantity, double unitPrice, double subtotal, double saleTotal, String paymentMethod) {
        this(saleId, 0, saleDate, productCode, productName, quantity, unitPrice, subtotal, saleTotal, paymentMethod);
    }

    public SaleDetailRow(int saleId, int productId, String saleDate, String productCode, String productName,
                         double quantity, double unitPrice, double subtotal, double saleTotal, String paymentMethod) {
        this.saleId = saleId;
        this.productId = productId;
        this.saleDate = saleDate;
        this.productCode = productCode != null ? productCode : "";
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.saleTotal = saleTotal;
        this.paymentMethod = paymentMethod;
    }

    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getSaleDate() { return saleDate; }
    public void setSaleDate(String saleDate) { this.saleDate = saleDate; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getSaleTotal() { return saleTotal; }
    public void setSaleTotal(double saleTotal) { this.saleTotal = saleTotal; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
