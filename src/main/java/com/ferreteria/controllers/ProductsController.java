package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSupplierRepository;
import com.ferreteria.services.ProductService;
import com.ferreteria.services.SupplierService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class ProductsController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Product> productsTable;

    @FXML
    private TableColumn<Product, String> colCode;

    @FXML
    private TableColumn<Product, String> colName;

    @FXML
    private TableColumn<Product, String> colDescription;

    @FXML
    private TableColumn<Product, Double> colPrice;

    @FXML
    private TableColumn<Product, Double> colStock;

    @FXML
    private TableColumn<Product, Double> colMinStock;

    @FXML
    private TableColumn<Product, String> colSupplier;

    private final ProductService productService;
    private final SupplierService supplierService;
    private final ObservableList<Product> tableData = FXCollections.observableArrayList();

    public ProductsController() {
        this.productService = new ProductService(new SQLiteProductRepository());
        this.supplierService = new SupplierService(new SQLiteSupplierRepository());
    }

    @FXML
    public void initialize() {
        setupTable();
        loadProducts();
        searchField.setOnAction(e -> onSearch());
    }

    private void setupTable() {
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colMinStock.setCellValueFactory(new PropertyValueFactory<>("minimumStock"));
        colSupplier.setCellValueFactory(new PropertyValueFactory<>("supplierName"));

        productsTable.setItems(tableData);
    }

    private void loadProducts() {
        List<Product> products = productService.getAllProducts();
        tableData.setAll(products);
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText();
        List<Product> result = productService.searchProducts(query);
        tableData.setAll(result);
    }

    @FXML
    private void onNewProduct() {
        ProductFormDialog dialog = new ProductFormDialog(null, supplierService.getAllSuppliers());
        dialog.showAndWait().ifPresent(product -> {
            try {
                productService.saveProduct(product);
                loadProducts();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onEditProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un producto para editar.");
            return;
        }
        ProductFormDialog dialog = new ProductFormDialog(selected, supplierService.getAllSuppliers());
        dialog.showAndWait().ifPresent(product -> {
            try {
                productService.saveProduct(product);
                loadProducts();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un producto para eliminar.");
            return;
        }
        productService.deleteProduct(selected);
        loadProducts();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

