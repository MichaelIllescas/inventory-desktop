package com.ferreteria.models;

public class Product {

    private Integer id;
    private String code;
    private String name;
    private String description;
    private double price;
    private double stock;
    private double minimumStock;
    private Integer supplierId;
    /** Nombre del proveedor (solo para mostrar en lista, no se persiste). */
    private String supplierName;
    /** Si true, no descuenta stock al vender (producto "Varios / Sin código"). */
    private boolean skipStock;
    /** Si true, producto importado de precarga — no aparece en inventario hasta que el cliente lo configure. */
    private boolean precarga;

    public Product() {
    }

    public Product(Integer id, String code, String name, String description, double price, double stock, double minimumStock, Integer supplierId) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.minimumStock = minimumStock;
        this.supplierId = supplierId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getStock() {
        return stock;
    }

    public void setStock(double stock) {
        this.stock = stock;
    }

    public double getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(double minimumStock) {
        this.minimumStock = minimumStock;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public boolean isSkipStock() {
        return skipStock;
    }

    public void setSkipStock(boolean skipStock) {
        this.skipStock = skipStock;
    }

    public boolean isPrecarga() {
        return precarga;
    }

    public void setPrecarga(boolean precarga) {
        this.precarga = precarga;
    }
}

