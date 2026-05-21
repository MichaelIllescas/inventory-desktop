package com.ferreteria.models;

public class CustomerCurrentAccountReportRow {
    private final int customerId;
    private final String customerName;
    private final double initialDebt;
    private final double periodSales;
    private final double periodPayments;
    private final double finalDebt;

    public CustomerCurrentAccountReportRow(int customerId, String customerName, double initialDebt, double periodSales, double periodPayments, double finalDebt) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.initialDebt = initialDebt;
        this.periodSales = periodSales;
        this.periodPayments = periodPayments;
        this.finalDebt = finalDebt;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public double getInitialDebt() {
        return initialDebt;
    }

    public double getPeriodSales() {
        return periodSales;
    }

    public double getPeriodPayments() {
        return periodPayments;
    }

    public double getFinalDebt() {
        return finalDebt;
    }
}

