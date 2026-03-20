package com.ferreteria.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class MainController {

    @FXML
    private StackPane contentArea;

    @FXML
    public void initialize() {
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        loadView("dashboard-view.fxml");
    }

    @FXML
    private void showProducts() {
        loadView("products-view.fxml");
    }

    @FXML
    private void showSales() {
        loadView("sales-view.fxml");
    }

    @FXML
    private void showSuppliers() {
        loadView("suppliers-view.fxml");
    }

    @FXML
    private void showInventory() {
        loadView("inventory-view.fxml");
    }

    @FXML
    private void showReports() {
        loadView("reports-view.fxml");
    }

    @FXML
    private void openImperialNet() {
        try {
            Desktop.getDesktop().browse(URI.create("https://imperial-net.com"));
        } catch (Exception e) {
            // Si no se puede abrir el navegador, ignorar
        }
    }

    private void loadView(String fxmlName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/" + fxmlName));
            Node node = loader.load();
            contentArea.getChildren().setAll(node);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la vista: " + fxmlName, e);
        }
    }
}

