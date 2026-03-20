package com.ferreteria.controllers;

import com.ferreteria.models.Supplier;
import com.ferreteria.repositories.sqlite.SQLiteSupplierRepository;
import com.ferreteria.services.SupplierService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class SuppliersController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Supplier> suppliersTable;

    @FXML
    private TableColumn<Supplier, String> colName;

    @FXML
    private TableColumn<Supplier, String> colPhone;

    @FXML
    private TableColumn<Supplier, String> colEmail;

    @FXML
    private TableColumn<Supplier, String> colAddress;

    private final SupplierService supplierService;
    private final ObservableList<Supplier> tableData = FXCollections.observableArrayList();

    public SuppliersController() {
        this.supplierService = new SupplierService(new SQLiteSupplierRepository());
    }

    @FXML
    public void initialize() {
        setupTable();
        loadSuppliers();
        searchField.setOnAction(e -> onSearch());
    }

    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));

        suppliersTable.setItems(tableData);
    }

    private void loadSuppliers() {
        List<Supplier> suppliers = supplierService.getAllSuppliers();
        tableData.setAll(suppliers);
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText();
        List<Supplier> result = supplierService.searchSuppliers(query);
        tableData.setAll(result);
    }

    @FXML
    private void onNewSupplier() {
        SupplierFormDialog dialog = new SupplierFormDialog(null);
        dialog.showAndWait().ifPresent(supplier -> {
            try {
                supplierService.saveSupplier(supplier);
                loadSuppliers();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onEditSupplier() {
        Supplier selected = suppliersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un proveedor para editar.");
            return;
        }
        SupplierFormDialog dialog = new SupplierFormDialog(selected);
        dialog.showAndWait().ifPresent(supplier -> {
            try {
                supplierService.saveSupplier(supplier);
                loadSuppliers();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteSupplier() {
        Supplier selected = suppliersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un proveedor para eliminar.");
            return;
        }
        supplierService.deleteSupplier(selected);
        loadSuppliers();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
