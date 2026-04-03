package com.ferreteria.controllers;

import com.ferreteria.models.SalesByDay;
import com.ferreteria.services.DashboardService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardController {

    private static final DecimalFormat MONEY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "AR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        MONEY_FORMAT = new DecimalFormat("$ #,##0.00", symbols);
    }

    @FXML
    private BorderPane dashboardRoot;

    @FXML
    private Label labelTotalProducts;

    @FXML
    private Label labelLowStock;

    @FXML
    private Label labelTodaySales;

    @FXML
    private Label labelMonthSales;

    @FXML
    private VBox chartContainer;

    private final DashboardService dashboardService = new DashboardService();

    @FXML
    public void initialize() {
        refreshData();
    }

    public void refreshData() {
        labelTotalProducts.setText(String.valueOf(dashboardService.getTotalProducts()));
        labelLowStock.setText(String.valueOf(dashboardService.getLowStockCount()));
        labelTodaySales.setText(formatMoney(dashboardService.getTodaySalesTotal()));
        labelMonthSales.setText(formatMoney(dashboardService.getMonthSalesTotal()));

        chartContainer.getChildren().clear();
        AreaChart<String, Number> chart = buildSalesChart();
        if (chart != null) {
            chart.prefHeightProperty().bind(chartContainer.heightProperty());
            chart.prefWidthProperty().bind(chartContainer.widthProperty());
            chartContainer.getChildren().add(chart);
        }
    }

    @FXML
    private void goToSales() {
        navigateTo("sales-view.fxml");
    }

    @FXML
    private void goToInventory() {
        navigateTo("inventory-view.fxml");
    }

    @FXML
    private void goToProducts() {
        navigateTo("products-view.fxml");
    }

    @FXML
    private void goToSuppliers() {
        navigateTo("suppliers-view.fxml");
    }

    private AreaChart<String, Number> buildSalesChart() {
        List<SalesByDay> data = completeLastDays(dashboardService.getSalesLastDays(7), 7);
        boolean hasSales = data.stream().anyMatch(day -> day.getTotal() > 0);
        if (!hasSales) {
            Label noData = new Label("Todavía no hay ventas registradas en los últimos 7 días");
            noData.getStyleClass().add("dashboard-empty-state");
            chartContainer.getChildren().add(noData);
            return null;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Día");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Importe vendido");
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(true);
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return formatMoneyAxis(value.doubleValue());
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle("Cómo fueron las ventas día por día");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setVerticalGridLinesVisible(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.getStyleClass().add("dashboard-chart");
        chart.getStyleClass().add("dashboard-area-chart");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Ventas");
        for (SalesByDay d : data) {
            String dayLabel = formatDayLabel(d.getDay());
            series.getData().add(new XYChart.Data<>(dayLabel, d.getTotal()));
        }
        chart.getData().add(series);
        return chart;
    }

    private String formatDayLabel(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate;
        return isoDate.substring(8, 10) + "/" + isoDate.substring(5, 7);
    }

    private List<SalesByDay> completeLastDays(List<SalesByDay> rawData, int days) {
        Map<String, Double> totalsByDay = new LinkedHashMap<>();
        for (SalesByDay item : rawData) {
            totalsByDay.put(item.getDay(), item.getTotal());
        }

        List<SalesByDay> completed = new java.util.ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            String key = day.toString();
            completed.add(new SalesByDay(key, totalsByDay.getOrDefault(key, 0.0)));
        }
        return completed;
    }

    private String formatMoneyAxis(double value) {
        return formatMoney(value);
    }

    private String formatMoney(double value) {
        return MONEY_FORMAT.format(value);
    }

    private void navigateTo(String fxmlName) {
        try {
            MainController mainController = MainController.getInstance();
            if (mainController != null) {
                mainController.openSection(fxmlName);
                return;
            }
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/" + fxmlName));
            Node node = loader.load();
            Parent parent = dashboardRoot.getParent();
            if (parent instanceof Pane container) {
                container.getChildren().setAll(node);
                return;
            }
            throw new IllegalStateException("No se encontro el contenedor principal.");
        } catch (IOException e) {
            showNavigationError("No se pudo abrir la vista: " + fxmlName, e);
        }
    }

    private void showNavigationError(String message, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        throw new RuntimeException(message, e);
    }
}