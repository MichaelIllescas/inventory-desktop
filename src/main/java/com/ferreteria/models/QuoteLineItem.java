package com.ferreteria.models;

import javafx.beans.property.*;

/**
 * Linea de un presupuesto. Puede venir de un producto de la BD o cargarse manualmente
 * (en ese caso productId queda en null y la descripcion la escribe el usuario).
 */
public class QuoteLineItem {

    private final ObjectProperty<Integer> productId = new SimpleObjectProperty<>(null);
    private final StringProperty code = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final DoubleProperty quantity = new SimpleDoubleProperty(1);
    private final DoubleProperty price = new SimpleDoubleProperty(0);
    private final DoubleProperty subtotal = new SimpleDoubleProperty(0);
    private boolean subtotalOverridden = false;

    public QuoteLineItem() {
        recalculateOnChange();
    }

    public QuoteLineItem(Product product, double quantity) {
        this.productId.set(product.getId());
        this.code.set(product.getCode() != null ? product.getCode() : "");
        this.description.set(product.getName());
        this.quantity.set(quantity);
        this.price.set(product.getPrice());
        this.subtotal.set(quantity * product.getPrice());
        recalculateOnChange();
    }

    /** Linea manual: descripcion, cantidad y precio los escribe el usuario. */
    public static QuoteLineItem manual(String description, double quantity, double price) {
        QuoteLineItem item = new QuoteLineItem();
        item.description.set(description);
        item.quantity.set(quantity);
        item.price.set(price);
        item.subtotal.set(quantity * price);
        return item;
    }

    private void recalculateOnChange() {
        quantity.addListener((obs, o, n) -> { if (!subtotalOverridden) subtotal.set(quantity.get() * price.get()); });
        price.addListener((obs, o, n) -> { if (!subtotalOverridden) subtotal.set(quantity.get() * price.get()); });
    }

    public boolean isManual() {
        return productId.get() == null;
    }

    public Integer getProductId() {
        return productId.get();
    }

    public void setProductId(Integer value) {
        productId.set(value);
    }

    public ObjectProperty<Integer> productIdProperty() {
        return productId;
    }

    public String getCode() {
        return code.get();
    }

    public void setCode(String value) {
        code.set(value == null ? "" : value);
    }

    public StringProperty codeProperty() {
        return code;
    }

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String value) {
        description.set(value == null ? "" : value);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public double getQuantity() {
        return quantity.get();
    }

    public void setQuantity(double value) {
        quantity.set(value);
    }

    public DoubleProperty quantityProperty() {
        return quantity;
    }

    public double getPrice() {
        return price.get();
    }

    public void setPrice(double value) {
        price.set(value);
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public double getSubtotal() {
        return subtotal.get();
    }

    /** Sobrescribe el subtotal sin tocar el precio unitario (descuentos, ajustes). */
    public void setSubtotal(double value) {
        subtotalOverridden = true;
        subtotal.set(value);
    }

    /**
     * Restaura el subtotal guardado en la BD. Solo lo marca como sobrescrito si no coincide
     * con cantidad x precio; si coincide, la linea sigue recalculando al editar cantidad o precio.
     */
    public void restoreSubtotal(double value) {
        subtotal.set(value);
        subtotalOverridden = Math.abs(value - quantity.get() * price.get()) > 0.005;
    }

    public DoubleProperty subtotalProperty() {
        return subtotal;
    }
}
