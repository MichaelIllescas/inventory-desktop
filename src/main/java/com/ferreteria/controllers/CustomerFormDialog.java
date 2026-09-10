package com.ferreteria.controllers;

import com.ferreteria.models.Customer;
import com.ferreteria.services.LicenseService;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class CustomerFormDialog extends Dialog<Customer> {

    private final TextField nameField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField addressField = new TextField();
    private final TextField taxIdField = new TextField();
    private final TextField creditLimitField = new TextField();

    private final Customer original;
    private final boolean showCreditLimit;

    public CustomerFormDialog(Customer customer) {
        this.original = customer;

        setTitle(customer == null ? "Nuevo cliente" : "Editar cliente");
        setHeaderText(null);

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        nameField.setPromptText("Nombre y apellido");
        phoneField.setPromptText("Opcional");
        addressField.setPromptText("Opcional");
        taxIdField.setPromptText("Opcional");

        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Teléfono:"), 0, 1);
        grid.add(phoneField, 1, 1);
        grid.add(new Label("Dirección:"), 0, 2);
        grid.add(addressField, 1, 2);
        grid.add(new Label("DNI / CUIT:"), 0, 3);
        grid.add(taxIdField, 1, 3);

        // El limite de credito solo tiene sentido con cuentas corrientes habilitadas.
        this.showCreditLimit = new LicenseService().isCurrentAccountsEnabled();
        if (showCreditLimit) {
            grid.add(new Label("Límite crédito:"), 0, 4);
            grid.add(creditLimitField, 1, 4);
        }

        if (customer != null) {
            nameField.setText(valueOrEmpty(customer.getName()));
            phoneField.setText(valueOrEmpty(customer.getPhone()));
            addressField.setText(valueOrEmpty(customer.getAddress()));
            taxIdField.setText(valueOrEmpty(customer.getTaxId()));
            if (customer.getCreditLimit() < 0) {
                creditLimitField.setText("");
                creditLimitField.setPromptText("Sin límite");
            } else {
                creditLimitField.setText(String.format("%.2f", customer.getCreditLimit()).replace('.', ','));
            }
        } else {
            creditLimitField.setText("");
            creditLimitField.setPromptText("Sin límite");
        }

        getDialogPane().setContent(grid);
        setResultConverter(dialogButton -> dialogButton == saveButtonType ? buildCustomerFromFields() : null);
    }

    private Customer buildCustomerFromFields() {
        Customer c = original != null ? original : new Customer();
        c.setName(trimToNull(nameField.getText()));
        c.setPhone(trimToNull(phoneField.getText()));
        c.setAddress(trimToNull(addressField.getText()));
        c.setTaxId(trimToNull(taxIdField.getText()));
        // Con el campo oculto se conserva el limite que ya tenia el cliente.
        if (showCreditLimit) {
            c.setCreditLimit(parseAmount(creditLimitField.getText()));
        } else if (original == null) {
            c.setCreditLimit(-1);
        }
        if (original == null) {
            c.setActive(true);
        }
        return c;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) return -1;
        String clean = value.trim().replace(".", "").replace(',', '.');
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
