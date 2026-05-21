package com.ferreteria.models;

public class CustomerAccountMovementRow {

    private final int id;
    private final String date;
    private final String type;
    private final double amount;
    private final Integer saleId;
    private final Integer paymentId;
    private final String notes;
    private final double runningBalance;

    public CustomerAccountMovementRow(
            int id,
            String date,
            String type,
            double amount,
            Integer saleId,
            Integer paymentId,
            String notes,
            double runningBalance
    ) {
        this.id = id;
        this.date = date;
        this.type = type;
        this.amount = amount;
        this.saleId = saleId;
        this.paymentId = paymentId;
        this.notes = notes;
        this.runningBalance = runningBalance;
    }

    public int getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public Integer getSaleId() {
        return saleId;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public String getNotes() {
        return notes;
    }

    public double getRunningBalance() {
        return runningBalance;
    }
}
