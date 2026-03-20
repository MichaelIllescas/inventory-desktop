package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class AdjustStockDialog extends Dialog<Double> {

    private final Spinner<Double> stockSpinner;

    public AdjustStockDialog(Product product) {
        setTitle("Ajustar stock");
        setHeaderText("Producto: " + (product.getName() != null ? product.getName() : product.getCode()));

        ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        double initial = product.getStock();
        stockSpinner = new Spinner<>(0.0, 999_999.99, initial, 0.01);
        stockSpinner.setEditable(true);
        stockSpinner.setPrefWidth(120);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        grid.add(new Label("Stock actual:"), 0, 0);
        grid.add(new Label(String.format("%.2f", product.getStock())), 1, 0);
        grid.add(new Label("Nuevo stock:"), 0, 1);
        grid.add(stockSpinner, 1, 1);

        getDialogPane().setContent(grid);

        setResultConverter(btn -> btn == saveButton ? stockSpinner.getValue() : null);
    }
}
