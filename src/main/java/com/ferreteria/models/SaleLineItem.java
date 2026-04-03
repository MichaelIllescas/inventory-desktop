package com.ferreteria.models;

import javafx.beans.property.*;
import javafx.beans.binding.Bindings;

/**
 * Línea del carrito de venta (antes de registrar). Cantidad en decimal (ej. kg para carne).
 */
public class SaleLineItem {

    private final IntegerProperty productId = new SimpleIntegerProperty();
    private final StringProperty productCode = new SimpleStringProperty();
    private final StringProperty productName = new SimpleStringProperty();
    private final DoubleProperty quantity = new SimpleDoubleProperty(1);
    private final DoubleProperty unitPrice = new SimpleDoubleProperty(0);
    private final DoubleProperty subtotal = new SimpleDoubleProperty(0);
    private boolean subtotalOverridden = false;

    public SaleLineItem(Product product, double quantity) {
        this.productId.set(product.getId());
        this.productCode.set(product.getCode() != null ? product.getCode() : "");
        this.productName.set(product.getName());
        this.quantity.set(quantity);
        this.unitPrice.set(product.getPrice());
        this.subtotal.set(quantity * product.getPrice());
        this.quantity.addListener((obs, o, n) -> { if (!subtotalOverridden) subtotal.set(this.quantity.get() * this.unitPrice.get()); });
        this.unitPrice.addListener((obs, o, n) -> { if (!subtotalOverridden) subtotal.set(this.quantity.get() * this.unitPrice.get()); });
    }

    public DoubleProperty subtotalProperty() {
        return subtotal;
    }

    public void setSubtotal(double value) {
        subtotalOverridden = true;
        subtotal.set(value);
    }

    public int getProductId() {
        return productId.get();
    }

    public IntegerProperty productIdProperty() {
        return productId;
    }

    public String getProductCode() {
        return productCode.get();
    }

    public StringProperty productCodeProperty() {
        return productCode;
    }

    public String getProductName() {
        return productName.get();
    }

    public StringProperty productNameProperty() {
        return productName;
    }

    public double getQuantity() {
        return quantity.get();
    }

    public void setQuantity(double quantity) {
        this.quantity.set(quantity);
    }

    public DoubleProperty quantityProperty() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice.get();
    }

    /** Precio efectivo cobrado: si el subtotal fue modificado, distribuye el ajuste por unidad. */
    public double getEffectiveUnitPrice() {
        if (subtotalOverridden && quantity.get() > 0) {
            return subtotal.get() / quantity.get();
        }
        return unitPrice.get();
    }

    public void setUnitPrice(double price) {
        this.unitPrice.set(price);
    }

    public DoubleProperty unitPriceProperty() {
        return unitPrice;
    }

    public double getSubtotal() {
        return subtotal.get();
    }
}
