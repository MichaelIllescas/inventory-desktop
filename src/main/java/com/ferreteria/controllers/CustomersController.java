package com.ferreteria.controllers;

import com.ferreteria.models.Customer;
import com.ferreteria.repositories.sqlite.SQLiteCustomerRepository;
import com.ferreteria.services.CustomerService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class CustomersController {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<Customer> customersTable;
    @FXML
    private TableColumn<Customer, String> colName;
    @FXML
    private TableColumn<Customer, String> colPhone;
    @FXML
    private TableColumn<Customer, String> colAddress;
    @FXML
    private TableColumn<Customer, Number> colCreditLimit;
    @FXML
    private Label summaryTotalCustomersLabel;
    @FXML
    private Label summaryCurrentFilterLabel;

    private final CustomerService customerService;
    private final ObservableList<Customer> tableData = FXCollections.observableArrayList();

    public CustomersController() {
        this.customerService = new CustomerService(new SQLiteCustomerRepository());
    }

    @FXML
    public void initialize() {
        setupTable();
        loadCustomers();
        searchField.setOnAction(e -> onSearch());
    }

    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colCreditLimit.setCellValueFactory(new PropertyValueFactory<>("creditLimit"));
        colCreditLimit.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });
        customersTable.setItems(tableData);
    }

    private void loadCustomers() {
        List<Customer> customers = customerService.getAllActiveCustomers();
        tableData.setAll(customers);
        updateSummary(customers);
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText();
        List<Customer> result = customerService.searchActiveCustomers(query);
        tableData.setAll(result);
        updateSummary(result);
    }

    @FXML
    private void onResetFilter() {
        searchField.clear();
        loadCustomers();
    }

    @FXML
    private void onNewCustomer() {
        CustomerFormDialog dialog = new CustomerFormDialog(null);
        dialog.showAndWait().ifPresent(customer -> {
            try {
                customerService.saveCustomer(customer);
                loadCustomers();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onEditCustomer() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un cliente para editar.");
            return;
        }
        CustomerFormDialog dialog = new CustomerFormDialog(selected);
        dialog.showAndWait().ifPresent(customer -> {
            try {
                customerService.saveCustomer(customer);
                loadCustomers();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteCustomer() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un cliente para eliminar.");
            return;
        }
        try {
            customerService.deactivateCustomer(selected);
            loadCustomers();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void updateSummary(List<Customer> customers) {
        if (summaryTotalCustomersLabel == null || summaryCurrentFilterLabel == null) return;
        summaryTotalCustomersLabel.setText(String.valueOf(customers.size()));
        String query = searchField != null && searchField.getText() != null ? searchField.getText().trim() : "";
        summaryCurrentFilterLabel.setText(query.isBlank() ? "Todos" : "Filtrado");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatCurrency(double total) {
        if (total < 0) {
            return "Sin límite";
        }
        String num = String.format("%.2f", total).replace('.', ',');
        int i = num.indexOf(',');
        if (i > 3) {
            StringBuilder sb = new StringBuilder(num);
            for (int j = i - 3; j > 0; j -= 3) {
                sb.insert(j, '.');
            }
            num = sb.toString();
        }
        return "$ " + num;
    }
}
