package com.ferreteria.controllers;

import com.ferreteria.models.SalesByDay;
import com.ferreteria.services.DashboardService;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public class DashboardController {

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
        labelTodaySales.setText(String.format("%.2f", dashboardService.getTodaySalesTotal()));
        labelMonthSales.setText(String.format("%.2f", dashboardService.getMonthSalesTotal()));

        chartContainer.getChildren().clear();
        BarChart<String, Number> chart = buildSalesChart();
        if (chart != null) {
            chart.prefHeightProperty().bind(chartContainer.heightProperty());
            chart.prefWidthProperty().bind(chartContainer.widthProperty());
            chartContainer.getChildren().add(chart);
        }
    }

    private BarChart<String, Number> buildSalesChart() {
        List<SalesByDay> data = dashboardService.getSalesLastDays(7);
        if (data.isEmpty()) {
            Label noData = new Label("Sin ventas en los últimos 7 días");
            noData.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");
            chartContainer.getChildren().add(noData);
            return null;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Día");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Total ($)");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Ventas por día");
        chart.setLegendVisible(false);
        chart.setCategoryGap(20);
        chart.setBarGap(8);
        chart.setAnimated(true);
        chart.getStyleClass().add("dashboard-chart");

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
}
