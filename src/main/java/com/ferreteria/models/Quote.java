package com.ferreteria.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Presupuesto. Es un documento informativo: no descuenta stock ni genera venta.
 */
public class Quote {

    private Integer id;
    private String date;
    private Integer customerId;
    private String customerName;
    private int validDays = 15;
    private String notes;
    private double total;
    private List<QuoteLineItem> items = new ArrayList<>();
    private Integer itemCount;

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

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public int getValidDays() {
        return validDays;
    }

    public void setValidDays(int validDays) {
        this.validDays = validDays;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public List<QuoteLineItem> getItems() {
        return items;
    }

    public void setItems(List<QuoteLineItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    /** Cantidad de items. En el listado viene contada por SQL, sin cargar las lineas. */
    public int getItemCount() {
        return itemCount != null ? itemCount : items.size();
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
