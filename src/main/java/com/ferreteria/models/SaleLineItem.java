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
    private final ReadOnlyDoubleWrapper subtotal = new ReadOnlyDoubleWrapper();

    public SaleLineItem(Product product, double quantity) {
        this.productId.set(product.getId());
        this.productCode.set(product.getCode() != null ? product.getCode() : "");
        this.productName.set(product.getName());
        this.quantity.set(quantity);
        this.unitPrice.set(product.getPrice());
        subtotal.bind(Bindings.multiply(this.quantity, this.unitPrice));
    }

    public ReadOnlyDoubleProperty subtotalProperty() {
        return subtotal.getReadOnlyProperty();
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

    public DoubleProperty unitPriceProperty() {
        return unitPrice;
    }

    public double getSubtotal() {
        return getUnitPrice() * getQuantity();
    }
}
