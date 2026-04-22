package com.ferreteria.controllers;

import com.ferreteria.util.AppLogger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class MainController {

    private static MainController instance;

    @FXML
    private StackPane contentArea;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnProducts;
    @FXML
    private Button btnSales;
    @FXML
    private Button btnSuppliers;
    @FXML
    private Button btnInventory;
    @FXML
    private Button btnReports;
    @FXML
    private Button btnExpenses;

    @FXML
    public void initialize() {
        instance = this;
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        setActiveSidebarButton(btnDashboard);
        loadView("dashboard-view.fxml");
    }

    @FXML
    private void showProducts() {
        setActiveSidebarButton(btnProducts);
        loadView("products-view.fxml");
    }

    @FXML
    private void showSales() {
        setActiveSidebarButton(btnSales);
        loadView("sales-view.fxml");
    }

    @FXML
    private void showSuppliers() {
        setActiveSidebarButton(btnSuppliers);
        loadView("suppliers-view.fxml");
    }

    @FXML
    private void showInventory() {
        setActiveSidebarButton(btnInventory);
        loadView("inventory-view.fxml");
    }

    @FXML
    private void showReports() {
        setActiveSidebarButton(btnReports);
        loadView("reports-view.fxml");
    }

    @FXML
    private void showExpenses() {
        setActiveSidebarButton(btnExpenses);
        loadView("expenses-view.fxml");
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
        AppLogger.info("MainController", "loadView", "Cargando vista: " + fxmlName);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/" + fxmlName));
            Node node = loader.load();
            contentArea.getChildren().setAll(node);
            AppLogger.info("MainController", "loadView", "Vista cargada OK: " + fxmlName);
        } catch (IOException e) {
            AppLogger.error("MainController", "loadView", "Error al cargar vista: " + fxmlName, e);
            throw new RuntimeException("No se pudo cargar la vista: " + fxmlName, e);
        }
    }

    public static MainController getInstance() {
        return instance;
    }

    public void openSection(String fxmlName) {
        Button activeButton = switch (fxmlName) {
            case "dashboard-view.fxml" -> btnDashboard;
            case "products-view.fxml" -> btnProducts;
            case "sales-view.fxml" -> btnSales;
            case "suppliers-view.fxml" -> btnSuppliers;
            case "inventory-view.fxml" -> btnInventory;
            case "reports-view.fxml" -> btnReports;
            case "expenses-view.fxml" -> btnExpenses;
            default -> null;
        };
        setActiveSidebarButton(activeButton);
        loadView(fxmlName);
    }

    private void setActiveSidebarButton(Button activeButton) {
        Button[] buttons = {btnDashboard, btnProducts, btnSales, btnSuppliers, btnInventory, btnReports, btnExpenses};
        for (Button button : buttons) {
            if (button == null) {
                continue;
            }
            button.getStyleClass().remove("sidebar-button-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("sidebar-button-active")) {
            activeButton.getStyleClass().add("sidebar-button-active");
        }
    }
}
