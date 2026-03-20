package com.ferreteria.controllers;

import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;
import com.ferreteria.services.ReportService;
import com.ferreteria.services.SaleService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportsController {

    private static final String VENTAS_POR_DIA = "Ventas por día";
    private static final String PRODUCTOS_MAS_VENDIDOS = "Productos más vendidos";
    private static final String VENTAS_CON_DETALLE = "Ventas con detalle";
    private static final int TOP_PRODUCTS_LIMIT = 50;
    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;
    @FXML
    private ComboBox<String> reportTypeCombo;
    @FXML
    private Label summaryLabel;
    @FXML
    private TableView<SalesByDay> tableByDay;
    @FXML
    private TableColumn<SalesByDay, String> colDay;
    @FXML
    private TableColumn<SalesByDay, Number> colDayTotal;
    @FXML
    private TableColumn<SalesByDay, Number> colDayCash;
    @FXML
    private TableColumn<SalesByDay, Number> colDayTransfer;
    @FXML
    private TableColumn<SalesByDay, Number> colDayDebit;
    @FXML
    private TableColumn<SalesByDay, Number> colDayCredit;
    @FXML
    private TableView<ProductSalesReport> tableProducts;
    @FXML
    private TableColumn<ProductSalesReport, String> colCode;
    @FXML
    private TableColumn<ProductSalesReport, String> colProductName;
    @FXML
    private TableColumn<ProductSalesReport, Number> colQty;
    @FXML
    private TableColumn<ProductSalesReport, Number> colRevenue;
    @FXML
    private TableView<SaleDetailRow> tableDetail;
    @FXML
    private TableColumn<SaleDetailRow, String> colDetailDate;
    @FXML
    private TableColumn<SaleDetailRow, Number> colDetailSaleId;
    @FXML
    private TableColumn<SaleDetailRow, String> colDetailCode;
    @FXML
    private TableColumn<SaleDetailRow, String> colDetailProduct;
    @FXML
    private TableColumn<SaleDetailRow, Number> colDetailQty;
    @FXML
    private TableColumn<SaleDetailRow, Number> colDetailPrice;
    @FXML
    private TableColumn<SaleDetailRow, Number> colDetailSubtotal;
    @FXML
    private TableColumn<SaleDetailRow, String> colDetailPayment;
    @FXML
    private Button btnDeleteSale;

    private final ReportService reportService = new ReportService();
    private final SaleService saleService = new SaleService(new SQLiteSaleRepository(), new SQLiteProductRepository());

    @FXML
    public void initialize() {
        reportTypeCombo.getItems().setAll(VENTAS_POR_DIA, PRODUCTOS_MAS_VENDIDOS, VENTAS_CON_DETALLE);
        reportTypeCombo.getSelectionModel().selectFirst();
        reportTypeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String s) { return s == null ? "" : s; }
            @Override
            public String fromString(String s) { return s; }
        });

        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.minusDays(6));
        dateTo.setValue(today);

        colDay.setCellValueFactory(new PropertyValueFactory<>("day"));
        colDay.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("");
                } else {
                    try {
                        LocalDate d = LocalDate.parse(item);
                        setText(d.format(DATE_DISPLAY));
                    } catch (Exception e) {
                        setText(item);
                    }
                }
            }
        });
        colDayTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
        colDayCash.setCellValueFactory(new PropertyValueFactory<>("cash"));
        colDayTransfer.setCellValueFactory(new PropertyValueFactory<>("transfer"));
        colDayDebit.setCellValueFactory(new PropertyValueFactory<>("debit"));
        colDayCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));

        colCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));
        colRevenue.setCellValueFactory(new PropertyValueFactory<>("totalRevenue"));

        colDetailDate.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        colDetailDate.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("");
                } else {
                    try {
                        LocalDate d = LocalDate.parse(item);
                        setText(d.format(DATE_DISPLAY));
                    } catch (Exception e) {
                        setText(item);
                    }
                }
            }
        });
        colDetailSaleId.setCellValueFactory(new PropertyValueFactory<>("saleId"));
        colDetailCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colDetailProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colDetailQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDetailPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colDetailSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        if (colDetailPayment != null) {
            colDetailPayment.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        }

        tableByDay.setVisible(true);
        tableProducts.setVisible(false);
        tableDetail.setVisible(false);

        reportTypeCombo.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> updateDeleteButtonState());
        tableDetail.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> updateDeleteButtonState());

        onGenerate();
    }

    private void updateDeleteButtonState() {
        boolean detailReport = VENTAS_CON_DETALLE.equals(reportTypeCombo.getSelectionModel().getSelectedItem());
        boolean hasSelection = tableDetail.getSelectionModel().getSelectedItem() != null;
        btnDeleteSale.setDisable(!detailReport || !hasSelection);
    }

    @FXML
    private void onThisWeek() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.minusDays(today.getDayOfWeek().getValue() - 1));
        dateTo.setValue(today);
    }

    @FXML
    private void onThisMonth() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.withDayOfMonth(1));
        dateTo.setValue(today);
    }

    @FXML
    private void onGenerate() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) {
            showError("Seleccioná las fechas desde y hasta.");
            return;
        }
        if (from.isAfter(to)) {
            showError("La fecha desde no puede ser mayor que la fecha hasta.");
            return;
        }

        String fromStr = from.toString();
        String toStr = to.toString();
        String type = reportTypeCombo.getSelectionModel().getSelectedItem();

        if (VENTAS_POR_DIA.equals(type)) {
            List<SalesByDay> rows = reportService.getSalesByDayInRange(fromStr, toStr);
            tableByDay.getItems().setAll(rows);
            tableByDay.setVisible(true);
            tableProducts.setVisible(false);
            tableDetail.setVisible(false);
            double total = reportService.getSalesTotalInRange(fromStr, toStr);
            summaryLabel.setText(formatSummaryTotal(total) + "  ·  " + rows.size() + " días con ventas");
            summaryLabel.setVisible(true);
        } else if (PRODUCTOS_MAS_VENDIDOS.equals(type)) {
            List<ProductSalesReport> rows = reportService.getTopProductsInRange(fromStr, toStr, TOP_PRODUCTS_LIMIT);
            tableProducts.getItems().setAll(rows);
            tableProducts.setVisible(true);
            tableByDay.setVisible(false);
            tableDetail.setVisible(false);
            double total = reportService.getSalesTotalInRange(fromStr, toStr);
            summaryLabel.setText(formatSummaryTotal(total) + "  ·  " + rows.size() + " productos");
            summaryLabel.setVisible(true);
        } else {
            List<SaleDetailRow> rows = reportService.getSaleDetailsInRange(fromStr, toStr);
            tableDetail.getItems().setAll(rows);
            tableDetail.setVisible(true);
            tableByDay.setVisible(false);
            tableProducts.setVisible(false);
            double total = reportService.getSalesTotalInRange(fromStr, toStr);
            long ventas = rows.stream().mapToInt(SaleDetailRow::getSaleId).distinct().count();
            summaryLabel.setText(formatSummaryTotal(total) + "  ·  " + ventas + " ventas, " + rows.size() + " líneas");
            summaryLabel.setVisible(true);
        }
        updateDeleteButtonState();
    }

    @FXML
    private void onDeleteSale() {
        SaleDetailRow selected = tableDetail.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int saleId = selected.getSaleId();
        ButtonType confirm = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar la venta Nº " + saleId + "? Se devolverá el stock de los productos al inventario.",
                confirm, cancel);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText("Anular venta");
        if (alert.showAndWait().orElse(cancel) != confirm) return;
        try {
            saleService.deleteSale(saleId);
            onGenerate();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "No se pudo eliminar: " + e.getMessage()).showAndWait();
        }
    }

    /** Formato visible: "Total: $ 18.180,00" (pesos, coma decimal). */
    private static String formatSummaryTotal(double total) {
        String num = String.format("%.2f", total).replace('.', ',');
        int i = num.indexOf(',');
        if (i > 3) {
            StringBuilder sb = new StringBuilder(num);
            for (int j = i - 3; j > 0; j -= 3) {
                sb.insert(j, '.');
            }
            num = sb.toString();
        }
        return "Total: $ " + num;
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }
}
