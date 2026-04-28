package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.models.Supplier;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSupplierRepository;
import com.ferreteria.services.ProductService;
import com.ferreteria.services.SupplierService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

public class ProductSelectionDialog extends Dialog<Product> {

    private final TextField searchField = new TextField();
    private final TableView<Product> table = new TableView<>();
    private final ProductService productService;
    private final SQLiteProductRepository productRepository;
    private final SupplierService supplierService;

    public ProductSelectionDialog(Window owner) {
        this.productRepository = new SQLiteProductRepository();
        this.productService = new ProductService(productRepository);
        this.supplierService = new SupplierService(new SQLiteSupplierRepository());
        setTitle("Añadir producto");
        setHeaderText("Busque y seleccione un producto");
        initOwner(owner);

        ButtonType addButton = new ButtonType("Añadir", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        TableColumn<Product, String> colCode = new TableColumn<>("Código");
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        TableColumn<Product, String> colName = new TableColumn<>("Nombre");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Product, String> colDesc = new TableColumn<>("Marca");
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        TableColumn<Product, Double> colPrice = new TableColumn<>("Precio");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colCode.setPrefWidth(120);
        colName.setPrefWidth(200);
        colDesc.setPrefWidth(140);
        colPrice.setPrefWidth(80);
        table.getColumns().addAll(colCode, colName, colDesc, colPrice);
        table.setPrefHeight(280);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    setResult(row.getItem());
                    close();
                }
            });
            return row;
        });

        searchField.setPromptText("Buscar por código o nombre... (Enter para buscar)");
        searchField.setOnAction(e -> doSearch());

        GridPane top = new GridPane();
        top.setHgap(8);
        top.setVgap(8);
        top.add(new Label("Buscar:"), 0, 0);
        top.add(searchField, 1, 0);
        GridPane.setMargin(searchField, new Insets(0, 0, 8, 0));
        Button searchBtn = new Button("Buscar");
        searchBtn.setOnAction(e -> doSearch());
        top.add(searchBtn, 2, 0);
        GridPane.setMargin(searchBtn, new Insets(0, 0, 8, 0));

        BorderPane content = new BorderPane();
        content.setTop(top);
        content.setCenter(table);
        content.setPadding(new Insets(10));
        BorderPane.setMargin(top, new Insets(10, 0, 0, 0));
        getDialogPane().setContent(content);

        setResultConverter(btn -> {
            if (btn != addButton) return null;
            Product selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return null;
            if (selected.isPrecarga()) {
                return activarPrecarga(selected);
            }
            return selected;
        });

        loadProducts();
        table.getSelectionModel().selectFirst();
    }

    private void loadProducts() {
        table.getItems().setAll(productRepository.findAllIncludingPrecarga());
    }

    private void doSearch() {
        String q = searchField.getText();
        List<Product> list = q == null || q.isBlank()
                ? productRepository.findAllIncludingPrecarga()
                : productRepository.searchIncludingPrecarga(q.trim());
        table.getItems().setAll(list);
        if (!list.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
    }

    private Product activarPrecarga(Product product) {
        product.setPrecarga(false);
        List<Supplier> suppliers = supplierService.getAllSuppliers();
        ProductFormDialog dialog = new ProductFormDialog(product, suppliers);
        dialog.setTitle("Configurar producto");
        dialog.setHeaderText("Este producto no tiene precio ni stock. Completá los datos para activarlo.");
        Optional<Product> result = dialog.showAndWait();
        if (result.isPresent()) {
            Product configured = result.get();
            configured.setPrecarga(false);
            try {
                productService.saveProduct(configured);
                return configured;
            } catch (IllegalArgumentException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText(null);
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
        return null;
    }

    public static Product show(Window owner) {
        ProductSelectionDialog d = new ProductSelectionDialog(owner);
        Optional<Product> result = d.showAndWait();
        return result.orElse(null);
    }
}
