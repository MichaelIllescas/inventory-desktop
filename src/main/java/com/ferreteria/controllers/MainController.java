package com.ferreteria.controllers;

import com.ferreteria.util.AppLogger;
import com.ferreteria.services.LicenseService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

public class MainController {

    private static MainController instance;
    private static final double SIDEBAR_COMPACT_BREAKPOINT = 1460;
    private static final double SIDEBAR_XCOMPACT_BREAKPOINT = 1260;
    private static final double APP_SHORT_HEIGHT_BREAKPOINT = 860;
    private static final double APP_XSHORT_HEIGHT_BREAKPOINT = 760;
    private static final String SIDEBAR_COMPACT_CLASS = "sidebar-compact";
    private static final String SIDEBAR_XCOMPACT_CLASS = "sidebar-xcompact";
    private static final String APP_SHORT_CLASS = "app-short";
    private static final String APP_XSHORT_CLASS = "app-xshort";
    private final LicenseService licenseService = new LicenseService();

    @FXML
    private BorderPane rootPane;
    @FXML
    private StackPane contentArea;
    @FXML
    private VBox sidebarBox;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnProducts;
    @FXML
    private Button btnSales;
    @FXML
    private Button btnCustomers;
    @FXML
    private Button btnCurrentAccount;
    @FXML
    private Button btnSuppliers;
    @FXML
    private Button btnInventory;
    @FXML
    private Button btnReports;
    @FXML
    private Button btnExpenses;
    @FXML
    private Button btnQuotes;
    @FXML
    private Button btnSettings;
    @FXML
    private javafx.scene.control.Label versionLabel;

    @FXML
    public void initialize() {
        instance = this;
        applyLicensedModules();
        applyEditionLabel();
        setupSidebarResponsiveMode();
        showDashboard();
    }

    private void applyLicensedModules() {
        if (btnCurrentAccount != null) {
            boolean enabled = licenseService.isCurrentAccountsEnabled();
            btnCurrentAccount.setVisible(enabled);
            btnCurrentAccount.setManaged(enabled);
        }
        if (btnQuotes != null) {
            boolean enabled = licenseService.isQuotesEnabled();
            btnQuotes.setVisible(enabled);
            btnQuotes.setManaged(enabled);
        }
    }

    /** Muestra la edicion instalada al lado del numero de version: "v3.2 - Plus". */
    private void applyEditionLabel() {
        if (versionLabel == null) {
            return;
        }
        String edition = licenseService.getEdition();
        String name = switch (edition == null ? "" : edition.toLowerCase()) {
            case "plus" -> "Plus";
            case "complete" -> "Completa";
            case "basic" -> "Básica";
            default -> edition;
        };
        if (name != null && !name.isBlank()) {
            versionLabel.setText(versionLabel.getText() + "  ·  " + name);
        }
    }

    private void setupSidebarResponsiveMode() {
        if (sidebarBox == null) return;
        sidebarBox.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            applySidebarResponsiveMode(newScene.getWidth());
            applyAppHeightMode(newScene.getHeight());
            newScene.widthProperty().addListener((o, oldW, newW) -> applySidebarResponsiveMode(newW.doubleValue()));
            newScene.heightProperty().addListener((o, oldH, newH) -> applyAppHeightMode(newH.doubleValue()));
        });
    }

    private void applySidebarResponsiveMode(double width) {
        if (sidebarBox == null) return;
        sidebarBox.getStyleClass().removeAll(SIDEBAR_COMPACT_CLASS, SIDEBAR_XCOMPACT_CLASS);
        if (width < SIDEBAR_XCOMPACT_BREAKPOINT) {
            sidebarBox.getStyleClass().addAll(SIDEBAR_COMPACT_CLASS, SIDEBAR_XCOMPACT_CLASS);
        } else if (width < SIDEBAR_COMPACT_BREAKPOINT) {
            sidebarBox.getStyleClass().add(SIDEBAR_COMPACT_CLASS);
        }
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
    private void showCustomers() {
        setActiveSidebarButton(btnCustomers);
        loadView("customers-view.fxml");
    }

    @FXML
    private void showCurrentAccount() {
        if (!licenseService.isCurrentAccountsEnabled()) {
            showDashboard();
            return;
        }
        setActiveSidebarButton(btnCurrentAccount);
        loadView("current-account-view.fxml");
    }

    @FXML
    private void showQuotes() {
        if (!licenseService.isQuotesEnabled()) {
            showDashboard();
            return;
        }
        setActiveSidebarButton(btnQuotes);
        loadView("quotes-view.fxml");
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
    private void showSettings() {
        setActiveSidebarButton(btnSettings);
        loadView("settings-view.fxml");
    }

    @FXML
    private void openImperialNet() {
        try {
            Desktop.getDesktop().browse(URI.create("https://imperial-net.com"));
        } catch (Exception e) {
            // Si no se puede abrir el navegador, ignorar
        }
    }

    private Object loadView(String fxmlName) {
        AppLogger.info("MainController", "loadView", "Cargando vista: " + fxmlName);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/" + fxmlName));
            Node node = loader.load();
            contentArea.getChildren().setAll(node);
            AppLogger.info("MainController", "loadView", "Vista cargada OK: " + fxmlName);
            return loader.getController();
        } catch (IOException e) {
            AppLogger.error("MainController", "loadView", "Error al cargar vista: " + fxmlName, e);
            throw new RuntimeException("No se pudo cargar la vista: " + fxmlName, e);
        }
    }

    public static MainController getInstance() {
        return instance;
    }

    public Object openSection(String fxmlName) {
        if ("current-account-view.fxml".equals(fxmlName) && !licenseService.isCurrentAccountsEnabled()) {
            fxmlName = "dashboard-view.fxml";
        }
        if ("quotes-view.fxml".equals(fxmlName) && !licenseService.isQuotesEnabled()) {
            fxmlName = "dashboard-view.fxml";
        }
        Button activeButton = switch (fxmlName) {
            case "dashboard-view.fxml" -> btnDashboard;
            case "products-view.fxml" -> btnProducts;
            case "sales-view.fxml" -> btnSales;
            case "customers-view.fxml" -> btnCustomers;
            case "current-account-view.fxml" -> btnCurrentAccount;
            case "suppliers-view.fxml" -> btnSuppliers;
            case "inventory-view.fxml" -> btnInventory;
            case "reports-view.fxml" -> btnReports;
            case "expenses-view.fxml" -> btnExpenses;
            case "quotes-view.fxml" -> btnQuotes;
            default -> null;
        };
        setActiveSidebarButton(activeButton);
        return loadView(fxmlName);
    }

    private void setActiveSidebarButton(Button activeButton) {
        Button[] buttons = {btnDashboard, btnProducts, btnSales, btnCustomers, btnCurrentAccount, btnSuppliers, btnInventory, btnReports, btnExpenses, btnQuotes, btnSettings};
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

    private void applyAppHeightMode(double height) {
        if (rootPane == null) return;
        rootPane.getStyleClass().removeAll(APP_SHORT_CLASS, APP_XSHORT_CLASS);
        if (height < APP_XSHORT_HEIGHT_BREAKPOINT) {
            rootPane.getStyleClass().addAll(APP_SHORT_CLASS, APP_XSHORT_CLASS);
        } else if (height < APP_SHORT_HEIGHT_BREAKPOINT) {
            rootPane.getStyleClass().add(APP_SHORT_CLASS);
        }
    }
}
