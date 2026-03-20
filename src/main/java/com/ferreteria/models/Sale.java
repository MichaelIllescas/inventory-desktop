package com.ferreteria.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Sale {

    private Integer id;
    private String date;
    private double total;
    private String paymentMethod;

    public Sale() {
    }

    public Sale(Integer id, String date, double total) {
        this.id = id;
        this.date = date;
        this.total = total;
    }

    public static String nowAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
