package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.models.Supplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;

public class ProductFormDialog extends Dialog<Product> {

    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField descriptionField = new TextField();
    private final TextField priceField = new TextField();
    private final TextField stockField = new TextField();
    private final TextField minStockField = new TextField();
    private final ComboBox<Supplier> supplierCombo = new ComboBox<>();

    private final Product original;

    public ProductFormDialog(Product product, List<Supplier> suppliers) {
        this.original = product;

        setTitle(product == null ? "Nuevo producto" : "Editar producto");
        setHeaderText(null);

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Código:"), 0, 0);
        grid.add(codeField, 1, 0);
        grid.add(new Label("Nombre:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("Descripción:"), 0, 2);
        grid.add(descriptionField, 1, 2);
        grid.add(new Label("Precio:"), 0, 3);
        grid.add(priceField, 1, 3);
        grid.add(new Label("Stock:"), 0, 4);
        grid.add(stockField, 1, 4);
        grid.add(new Label("Stock mínimo:"), 0, 5);
        grid.add(minStockField, 1, 5);
        grid.add(new Label("Proveedor:"), 0, 6);
        supplierCombo.setMaxWidth(Double.MAX_VALUE);
        supplierCombo.setItems(FXCollections.observableList(suppliers));
        supplierCombo.setConverter(new javafx.util.StringConverter<Supplier>() {
            @Override
            public String toString(Supplier s) {
                return s == null ? "(ninguno)" : s.getName();
            }
            @Override
            public Supplier fromString(String string) {
                return null;
            }
        });
        grid.add(supplierCombo, 1, 6);

        if (product != null) {
            codeField.setText(product.getCode() != null ? product.getCode() : "");
            nameField.setText(product.getName() != null ? product.getName() : "");
            descriptionField.setText(product.getDescription() != null ? product.getDescription() : "");
            priceField.setText(String.valueOf(product.getPrice()));
            stockField.setText(String.valueOf(product.getStock()));
            minStockField.setText(String.valueOf(product.getMinimumStock()));
            if (product.getSupplierId() != null && !suppliers.isEmpty()) {
                suppliers.stream().filter(s -> s.getId().equals(product.getSupplierId())).findFirst().ifPresent(supplierCombo::setValue);
            }
        }
        if (product == null) {
            codeField.setPromptText("Ej: PROD-001");
        }

        getDialogPane().setContent(grid);

        // Botón Guardar: no usarlo como botón por defecto para Enter (especialmente cuando el escáner envía Enter).
        final Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.setDefaultButton(false);

        // Evitar que Enter (o click en Guardar) cierre el diálogo si faltan datos obligatorios o hay errores.
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (!isFormValid()) {
                evt.consume();
            }
        });

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType && isFormValid()) {
                return buildProductFromFields();
            }
            return null;
        });

        setOnShown(event -> Platform.runLater(() -> {
            codeField.requestFocus();
            codeField.selectAll();
        }));
    }

    private Product buildProductFromFields() {
        Product p = original != null ? original : new Product();
        p.setCode(codeField.getText() != null ? codeField.getText().trim() : null);
        p.setName(nameField.getText());
        p.setDescription(descriptionField.getText());
        p.setPrice(parseDouble(priceField.getText()));
        p.setStock(parseDouble(stockField.getText()));
        p.setMinimumStock(parseDouble(minStockField.getText()));
        Supplier sel = supplierCombo.getValue();
        p.setSupplierId(sel != null ? sel.getId() : null);
        return p;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isFormValid() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (name.isEmpty()) {
            showError("El nombre del producto es obligatorio.");
            return false;
        }
        double price = parseDouble(priceField.getText());
        if (price < 0) {
            showError("El precio no puede ser negativo.");
            return false;
        }
        double stock = parseDouble(stockField.getText());
        if (stock < 0) {
            showError("El stock no puede ser negativo.");
            return false;
        }
        double minStock = parseDouble(minStockField.getText());
        if (minStock < 0) {
            showError("El stock mínimo no puede ser negativo.");
            return false;
        }
        return true;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
