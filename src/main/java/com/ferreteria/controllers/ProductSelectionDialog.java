package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.services.ProductService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/**
 * Diálogo para elegir un producto y añadirlo a la venta (cantidad por defecto 1).
 */
public class ProductSelectionDialog extends Dialog<Product> {

    private final TextField searchField = new TextField();
    private final TableView<Product> table = new TableView<>();
    private final ProductService productService;

    public ProductSelectionDialog(Window owner) {
        this.productService = new ProductService(new SQLiteProductRepository());
        setTitle("Añadir producto");
        setHeaderText("Busque y seleccione un producto");
        initOwner(owner);

        ButtonType addButton = new ButtonType("Añadir", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        TableColumn<Product, String> colCode = new TableColumn<>("Código");
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        TableColumn<Product, String> colName = new TableColumn<>("Nombre");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Product, Double> colPrice = new TableColumn<>("Precio");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colCode.setPrefWidth(120);
        colName.setPrefWidth(220);
        colPrice.setPrefWidth(80);
        table.getColumns().addAll(colCode, colName, colPrice);
        table.setPrefHeight(280);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        searchField.setPromptText("Buscar por código o nombre... (Enter para buscar)");
        searchField.setOnAction(e -> doSearch());

        GridPane top = new GridPane();
        top.setHgap(8);
        top.setVgap(8);
        top.add(new Label("Buscar:"), 0, 0);
        top.add(searchField, 1, 0);
        Button searchBtn = new Button("Buscar");
        searchBtn.setOnAction(e -> doSearch());
        top.add(searchBtn, 2, 0);

        BorderPane content = new BorderPane();
        content.setTop(top);
        content.setCenter(table);
        content.setPadding(new Insets(10));
        getDialogPane().setContent(content);

        setResultConverter(btn -> btn == addButton ? table.getSelectionModel().getSelectedItem() : null);

        loadProducts();
        table.getSelectionModel().selectFirst();
    }

    private void loadProducts() {
        table.getItems().setAll(productService.getAllProducts());
    }

    private void doSearch() {
        String q = searchField.getText();
        List<Product> list = productService.searchProducts(q);
        table.getItems().setAll(list);
        if (!list.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
    }

    public static Product show(Window owner) {
        ProductSelectionDialog d = new ProductSelectionDialog(owner);
        Optional<Product> result = d.showAndWait();
        return result.orElse(null);
    }
}
