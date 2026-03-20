package com.ferreteria.models;

/**
 * Ventas agregadas por día para gráficos.
 */
public class SalesByDay {

    private String day;
    private double total;
    private double cash;
    private double transfer;
    private double debit;
    private double credit;

    public SalesByDay(String day, double total) {
        this.day = day;
        this.total = total;
    }

    public SalesByDay(String day, double total, double cash, double transfer, double debit, double credit) {
        this.day = day;
        this.total = total;
        this.cash = cash;
        this.transfer = transfer;
        this.debit = debit;
        this.credit = credit;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public double getCash() {
        return cash;
    }

    public void setCash(double cash) {
        this.cash = cash;
    }

    public double getTransfer() {
        return transfer;
    }

    public void setTransfer(double transfer) {
        this.transfer = transfer;
    }

    public double getDebit() {
        return debit;
    }

    public void setDebit(double debit) {
        this.debit = debit;
    }

    public double getCredit() {
        return credit;
    }

    public void setCredit(double credit) {
        this.credit = credit;
    }
}
