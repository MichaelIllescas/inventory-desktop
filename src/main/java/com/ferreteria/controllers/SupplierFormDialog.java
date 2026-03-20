package com.ferreteria.controllers;

import com.ferreteria.models.Supplier;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class SupplierFormDialog extends Dialog<Supplier> {

    private final TextField nameField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField addressField = new TextField();

    private final Supplier original;

    public SupplierFormDialog(Supplier supplier) {
        this.original = supplier;

        setTitle(supplier == null ? "Nuevo proveedor" : "Editar proveedor");
        setHeaderText(null);

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Teléfono:"), 0, 1);
        grid.add(phoneField, 1, 1);
        grid.add(new Label("Email:"), 0, 2);
        grid.add(emailField, 1, 2);
        grid.add(new Label("Dirección:"), 0, 3);
        grid.add(addressField, 1, 3);

        if (supplier != null) {
            nameField.setText(supplier.getName() != null ? supplier.getName() : "");
            phoneField.setText(supplier.getPhone() != null ? supplier.getPhone() : "");
            emailField.setText(supplier.getEmail() != null ? supplier.getEmail() : "");
            addressField.setText(supplier.getAddress() != null ? supplier.getAddress() : "");
        }

        getDialogPane().setContent(grid);

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return buildSupplierFromFields();
            }
            return null;
        });
    }

    private Supplier buildSupplierFromFields() {
        Supplier s = original != null ? original : new Supplier();
        s.setName(nameField.getText() != null ? nameField.getText().trim() : null);
        s.setPhone(phoneField.getText() != null ? phoneField.getText().trim() : null);
        s.setEmail(emailField.getText() != null ? emailField.getText().trim() : null);
        s.setAddress(addressField.getText() != null ? addressField.getText().trim() : null);
        return s;
    }
}
