package com.ferreteria.models;

/**
 * Fila de reporte: producto con cantidades y monto vendido en un período.
 */
public class ProductSalesReport {

    private String productCode;
    private String productName;
    private double quantitySold;
    private double totalRevenue;

    public ProductSalesReport(String productCode, String productName, double quantitySold, double totalRevenue) {
        this.productCode = productCode;
        this.productName = productName;
        this.quantitySold = quantitySold;
        this.totalRevenue = totalRevenue;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public double getQuantitySold() {
        return quantitySold;
    }

    public void setQuantitySold(double quantitySold) {
        this.quantitySold = quantitySold;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
}
