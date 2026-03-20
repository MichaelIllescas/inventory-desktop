package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.models.ReplenishmentItem;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.services.ProductService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class InventoryController {

    @FXML
    private TextField searchField;

    @FXML
    private RadioButton radioAll;

    @FXML
    private RadioButton radioLowStock;

    @FXML
    private TableView<Product> stockTable;

    @FXML
    private TableColumn<Product, String> colCode;

    @FXML
    private TableColumn<Product, String> colName;

    @FXML
    private TableColumn<Product, Double> colStock;

    @FXML
    private TableColumn<Product, Double> colMinStock;

    @FXML
    private TableColumn<Product, String> colSupplier;

    @FXML
    private TableView<ReplenishmentItem> replenishmentTable;

    @FXML
    private TableColumn<ReplenishmentItem, String> repColCode;

    @FXML
    private TableColumn<ReplenishmentItem, String> repColName;

    @FXML
    private TableColumn<ReplenishmentItem, Double> repColStock;

    @FXML
    private TableColumn<ReplenishmentItem, Double> repColMin;

    @FXML
    private TableColumn<ReplenishmentItem, String> repColSupplier;

    @FXML
    private TableColumn<ReplenishmentItem, String> repColPhone;

    @FXML
    private TableColumn<ReplenishmentItem, String> repColEmail;

    private final ProductService productService;
    private final ObservableList<Product> stockData = FXCollections.observableArrayList();
    private final ObservableList<ReplenishmentItem> replenishmentData = FXCollections.observableArrayList();

    public InventoryController() {
        this.productService = new ProductService(new SQLiteProductRepository());
    }

    @FXML
    public void initialize() {
        javafx.scene.control.ToggleGroup filterGroup = new javafx.scene.control.ToggleGroup();
        radioAll.setToggleGroup(filterGroup);
        radioLowStock.setToggleGroup(filterGroup);
        filterGroup.selectedToggleProperty().addListener((o, oldVal, newVal) -> applyFilter());

        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colMinStock.setCellValueFactory(new PropertyValueFactory<>("minimumStock"));
        colSupplier.setCellValueFactory(new PropertyValueFactory<>("supplierName"));

        repColCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        repColName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        repColStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        repColMin.setCellValueFactory(new PropertyValueFactory<>("minimumStock"));
        repColSupplier.setCellValueFactory(new PropertyValueFactory<>("supplierName"));
        repColPhone.setCellValueFactory(new PropertyValueFactory<>("supplierPhone"));
        repColEmail.setCellValueFactory(new PropertyValueFactory<>("supplierEmail"));

        stockTable.setItems(stockData);
        replenishmentTable.setItems(replenishmentData);

        searchField.setOnAction(e -> onSearch());
        loadStock();
        loadReplenishment();
    }

    private void applyFilter() {
        if (radioLowStock.isSelected()) {
            stockData.setAll(productService.getLowStockProducts());
        } else {
            loadStock();
        }
    }

    private void loadStock() {
        String q = searchField.getText();
        List<Product> list = productService.searchProducts(q);
        stockData.setAll(list);
    }

    private void loadReplenishment() {
        replenishmentData.setAll(productService.getReplenishmentList());
    }

    @FXML
    private void onSearch() {
        applyFilter();
    }

    @FXML
    private void onAdjustStock() {
        Product selected = stockTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Seleccione un producto para ajustar el stock.");
            return;
        }
        AdjustStockDialog dialog = new AdjustStockDialog(selected);
        dialog.showAndWait().ifPresent(newStock -> {
            try {
                selected.setStock(newStock);
                productService.saveProduct(selected);
                loadStock();
                loadReplenishment();
                showInfo("Stock actualizado correctamente.");
            } catch (IllegalArgumentException e) {
                showError(e.getMessage());
            }
        });
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Aviso");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
