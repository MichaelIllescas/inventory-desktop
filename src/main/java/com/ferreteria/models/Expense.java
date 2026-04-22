package com.ferreteria.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Expense {

    private int id;
    private String date;
    private String description;
    private String category;
    private double amount;

    public Expense() {}

    public Expense(String date, String description, String category, double amount) {
        this.date = date;
        this.description = description;
        this.category = category;
        this.amount = amount;
    }

    public static String nowAsString() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
