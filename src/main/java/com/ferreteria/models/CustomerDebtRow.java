package com.ferreteria.models;

public class CustomerDebtRow {

    private final int saleId;
    private final String saleDate;
    private final double saleTotal;
    private final double appliedAmount;
    private final double pendingAmount;

    public CustomerDebtRow(int saleId, String saleDate, double saleTotal, double appliedAmount, double pendingAmount) {
        this.saleId = saleId;
        this.saleDate = saleDate;
        this.saleTotal = saleTotal;
        this.appliedAmount = appliedAmount;
        this.pendingAmount = pendingAmount;
    }

    public int getSaleId() {
        return saleId;
    }

    public String getSaleDate() {
        return saleDate;
    }

    public double getSaleTotal() {
        return saleTotal;
    }

    public double getAppliedAmount() {
        return appliedAmount;
    }

    public double getPendingAmount() {
        return pendingAmount;
    }

    public String getStatus() {
        if (pendingAmount <= 0.000001d) {
            return "PAGADA";
        }
        if (appliedAmount <= 0.000001d) {
            return "PENDIENTE";
        }
        return "PARCIAL";
    }
}
