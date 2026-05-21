package com.ferreteria.controllers;

import com.ferreteria.models.Customer;
import com.ferreteria.models.CustomerDebtRow;
import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.models.CustomerCurrentAccountReportRow;
import com.ferreteria.repositories.sqlite.SQLiteCustomerRepository;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;
import com.ferreteria.services.CurrentAccountService;
import com.ferreteria.services.CustomerService;
import com.ferreteria.services.ReportService;
import com.ferreteria.services.SaleService;
import com.ferreteria.util.CurrentAccountPdfExporter;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import static javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReportsController {

    private static final String VENTAS_POR_DIA = "Por dia";
    private static final String PRODUCTOS_MAS_VENDIDOS = "Productos mas vendidos";
    private static final String VENTAS_CON_DETALLE = "Con detalle";
    private static final String RENTABILIDAD = "Rentabilidad";
    private static final String REPORTE_CC = "Reporte CC";
    private static final int TOP_PRODUCTS_LIMIT = 50;
    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private DatePicker dateFrom;
    @FXML
    private ComboBox<String> comboTimeFrom;
    @FXML
    private DatePicker dateTo;
    @FXML
    private ComboBox<String> comboTimeTo;
    @FXML
    private ComboBox<String> reportTypeCombo;
    @FXML private HBox summaryCards;
    @FXML private VBox sCard1, sCard2, sCard3, sCard4, sCard5, sCard6;
    @FXML private Label sCard1Title, sCard1Value;
    @FXML private Label sCard2Title, sCard2Value;
    @FXML private Label sCard3Title, sCard3Value;
    @FXML private Label sCard4Title, sCard4Value;
    @FXML private Label sCard5Title, sCard5Value;
    @FXML private Label sCard6Title, sCard6Value;
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
    private TableColumn<SalesByDay, Number> colDayCurrentAccount;
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
    private TableColumn<SaleDetailRow, Number> colDetailSaleTotal;
    @FXML
    private TableColumn<SaleDetailRow, String> colDetailPayment;
    @FXML
    private TableView<CustomerCurrentAccountReportRow> tableCurrentAccount;
    @FXML
    private TableColumn<CustomerCurrentAccountReportRow, String> colCcCustomer;
    @FXML
    private TableColumn<CustomerCurrentAccountReportRow, Number> colCcSales;
    @FXML
    private TableColumn<CustomerCurrentAccountReportRow, Number> colCcPayments;
    @FXML
    private TableColumn<CustomerCurrentAccountReportRow, Number> colCcFinalDebt;
    @FXML
    private TableColumn<CustomerCurrentAccountReportRow, Void> colCcExport;
    @FXML
    private BorderPane reportsRoot;
    @FXML
    private Label viewTitleLabel;
    @FXML
    private Button btnDeleteSale;
    @FXML
    private VBox profitPanel;
    @FXML
    private Label profitDevengadoLabel;
    @FXML
    private Label profitCobradoLabel;
    @FXML
    private Label profitGastosLabel;
    @FXML
    private Label profitNetoCajaLabel;
    @FXML
    private Label profitCcSalesLabel;
    @FXML
    private Label profitCcPaymentsLabel;
    @FXML
    private Label profitLine1;
    @FXML
    private Label profitLine2;
    @FXML
    private Label profitLine3;
    @FXML
    private Label profitLine4;
    @FXML
    private LineChart<String, Number> profitLineChart;

    private final ReportService reportService = new ReportService();
    private final SaleService saleService = new SaleService(new SQLiteSaleRepository(), new SQLiteProductRepository());
    private final com.ferreteria.repositories.sqlite.SQLiteExpenseRepository expenseRepo = new com.ferreteria.repositories.sqlite.SQLiteExpenseRepository();
    private final CurrentAccountService currentAccountService = new CurrentAccountService();
    private final CustomerService customerService = new CustomerService(new SQLiteCustomerRepository());

    @FXML
    public void initialize() {
        tableByDay.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableProducts.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableDetail.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableCurrentAccount.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        reportTypeCombo.getItems().setAll(VENTAS_POR_DIA, VENTAS_CON_DETALLE, PRODUCTOS_MAS_VENDIDOS, RENTABILIDAD, REPORTE_CC);
        reportTypeCombo.getSelectionModel().selectFirst();

        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.minusDays(6));
        dateTo.setValue(today);

        List<String> timeSlots = buildTimeSlots();
        comboTimeFrom.getItems().setAll(timeSlots);
        comboTimeFrom.getSelectionModel().select("00:00");
        comboTimeTo.getItems().setAll(timeSlots);
        comboTimeTo.getSelectionModel().select("23:59");

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
        colDayCurrentAccount.setCellValueFactory(new PropertyValueFactory<>("currentAccount"));
        for (var col : List.of(colDayTotal, colDayCash, colDayTransfer, colDayDebit, colDayCredit, colDayCurrentAccount)) {
            col.setCellFactory(tc -> new TableCell<>() {
                @Override protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
                }
            });
        }

        colCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));
        colRevenue.setCellValueFactory(new PropertyValueFactory<>("totalRevenue"));
        colQty.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatQuantity(item.doubleValue()));
            }
        });
        colRevenue.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });

        colDetailDate.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        colDetailDate.setCellFactory(tc -> new TableCell<>() {
            private static final DateTimeFormatter DT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("");
                } else {
                    try {
                        setText(LocalDateTime.parse(item, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DT_DISPLAY));
                    } catch (Exception e) {
                        try {
                            setText(LocalDate.parse(item).format(DATE_DISPLAY));
                        } catch (Exception ex) {
                            setText(item);
                        }
                    }
                }
            }
        });
        colDetailSaleId.setCellValueFactory(new PropertyValueFactory<>("saleId"));
        colDetailCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colDetailProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colDetailQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDetailQty.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatQuantity(item.doubleValue()));
            }
        });
        colDetailPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colDetailPrice.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });
        colDetailSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        colDetailSubtotal.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });
        if (colDetailSaleTotal != null) {
            colDetailSaleTotal.setCellValueFactory(new PropertyValueFactory<>("saleTotal"));
            colDetailSaleTotal.setCellFactory(tc -> new TableCell<>() {
                @Override protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
                }
            });
        }
        if (colDetailPayment != null) {
            colDetailPayment.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        }

        colCcCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colCcSales.setCellValueFactory(new PropertyValueFactory<>("periodSales"));
        colCcPayments.setCellValueFactory(new PropertyValueFactory<>("periodPayments"));
        colCcFinalDebt.setCellValueFactory(new PropertyValueFactory<>("finalDebt"));
        for (TableColumn<CustomerCurrentAccountReportRow, Number> col : List.of(colCcSales, colCcPayments)) {
            col.setCellFactory(tc -> new TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
                }
            });
        }
        colCcFinalDebt.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    double v = item.doubleValue();
                    if (v < -0.005) {
                        setText("A favor: " + formatCurrency(-v));
                        setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    } else {
                        setText(formatCurrency(v));
                        setStyle("");
                    }
                }
            }
        });

        if (colCcExport != null) {
            colCcExport.setCellFactory(tc -> new TableCell<>() {
                private final Button btn = new Button("↓ PDF");
                {
                    btn.getStyleClass().add("cc-export-btn");
                    btn.setOnAction(e -> {
                        CustomerCurrentAccountReportRow row = getTableView().getItems().get(getIndex());
                        exportCustomerPdf(row);
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            });
        }

        tableByDay.setVisible(true);
        tableByDay.setManaged(true);
        tableProducts.setVisible(false);
        tableProducts.setManaged(false);
        tableDetail.setVisible(false);
        tableDetail.setManaged(false);
        tableCurrentAccount.setVisible(false);
        tableCurrentAccount.setManaged(false);
        showProfitPanel(false);

        reportTypeCombo.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> {
            updateDeleteButtonState();
            if (viewTitleLabel != null && v != null) {
                viewTitleLabel.setText(getTitleForType(v));
            }
        });
        tableDetail.getSelectionModel().selectedItemProperty().addListener((o, old, v) -> updateDeleteButtonState());

        reportsRoot.widthProperty().addListener((obs, old, w) -> {
            reportsRoot.getStyleClass().removeAll("reports-compact", "reports-xcompact");
            if (w.doubleValue() < 860) {
                reportsRoot.getStyleClass().add("reports-xcompact");
            } else if (w.doubleValue() < 1100) {
                reportsRoot.getStyleClass().add("reports-compact");
            }
        });

        onGenerate();
    }

    private void updateDeleteButtonState() {
        boolean detailReport = VENTAS_CON_DETALLE.equals(reportTypeCombo.getSelectionModel().getSelectedItem());
        boolean hasSelection = tableDetail.getSelectionModel().getSelectedItem() != null;
        btnDeleteSale.setDisable(!detailReport || !hasSelection);
    }

    @FXML
    private void onToday() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today);
        dateTo.setValue(today);
        resetTimeRange();
    }

    @FXML
    private void onThisWeek() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.minusDays(today.getDayOfWeek().getValue() - 1));
        dateTo.setValue(today);
        resetTimeRange();
    }

    @FXML
    private void onThisMonth() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.withDayOfMonth(1));
        dateTo.setValue(today);
        resetTimeRange();
    }

    @FXML
    private void onThisYear() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.withDayOfYear(1));
        dateTo.setValue(today);
        resetTimeRange();
    }

    private void resetTimeRange() {
        comboTimeFrom.getSelectionModel().select("00:00");
        comboTimeTo.getSelectionModel().select("23:59");
    }

    private static List<String> buildTimeSlots() {
        List<String> slots = new java.util.ArrayList<>();
        for (int h = 0; h < 24; h++) {
            slots.add(String.format("%02d:00", h));
            slots.add(String.format("%02d:30", h));
        }
        slots.add("23:59");
        return slots;
    }

    private String buildDateTimeString(LocalDate date, ComboBox<String> comboTime) {
        String time = comboTime.getValue();
        if (time == null) time = "00:00";
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int min = Integer.parseInt(parts[1]);
        return LocalDateTime.of(date, LocalTime.of(hour, min, 0))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @FXML
    private void onGenerate() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) {
            showError("Selecciona las fechas desde y hasta.");
            return;
        }

        String fromStr = buildDateTimeString(from, comboTimeFrom);
        String toStr = buildDateTimeString(to, comboTimeTo);

        if (fromStr.compareTo(toStr) > 0) {
            showError("La fecha/hora desde no puede ser mayor que la fecha/hora hasta.");
            return;
        }
        String type = reportTypeCombo.getSelectionModel().getSelectedItem();

        double totalExpenses = expenseRepo.getTotalInRange(fromStr, toStr);
        double totalDevengado = reportService.getSalesTotalInRange(fromStr, toStr);
        double totalCobrado = reportService.getCollectedTotalInRange(fromStr, toStr);
        double totalVentasCC = reportService.getCurrentAccountSalesTotalInRange(fromStr, toStr);
        double totalPagosCC = reportService.getCurrentAccountPaymentsTotalInRange(fromStr, toStr);
        double netoDevengado = totalDevengado - totalExpenses;
        double netoCaja = totalCobrado - totalExpenses;

        if (VENTAS_POR_DIA.equals(type)) {
            List<SalesByDay> rows = reportService.getSalesByDayInRange(fromStr, toStr);
            tableByDay.getItems().setAll(rows);
            tableByDay.setVisible(true);
            tableByDay.setManaged(true);
            tableCurrentAccount.setVisible(false);
            tableCurrentAccount.setManaged(false);
            tableProducts.setVisible(false);
            tableProducts.setManaged(false);
            tableDetail.setVisible(false);
            tableDetail.setManaged(false);
            showProfitPanel(false);
            double dailyAverage = rows.isEmpty() ? 0 : totalDevengado / rows.size();
            populateSummaryCards(totalDevengado, totalCobrado, totalVentasCC, totalPagosCC,
                    "Días con ventas", String.valueOf(rows.size()),
                    "Promedio diario", formatCurrency(dailyAverage));
            setSummaryVisible(true);
        } else if (PRODUCTOS_MAS_VENDIDOS.equals(type)) {
            List<ProductSalesReport> rows = reportService.getTopProductsInRange(fromStr, toStr, TOP_PRODUCTS_LIMIT);
            tableProducts.getItems().setAll(rows);
            tableProducts.setVisible(true);
            tableProducts.setManaged(true);
            tableCurrentAccount.setVisible(false);
            tableCurrentAccount.setManaged(false);
            tableByDay.setVisible(false);
            tableByDay.setManaged(false);
            tableDetail.setVisible(false);
            tableDetail.setManaged(false);
            showProfitPanel(false);
            double totalUnits = rows.stream().mapToDouble(ProductSalesReport::getQuantitySold).sum();
            populateSummaryCards(totalDevengado, totalCobrado, totalVentasCC, totalPagosCC,
                    "Productos en ranking", String.valueOf(rows.size()),
                    "Unidades vendidas", formatQuantity(totalUnits));
            setSummaryVisible(true);
        } else if (VENTAS_CON_DETALLE.equals(type)) {
            List<SaleDetailRow> rows = reportService.getSaleDetailsInRange(fromStr, toStr);
            tableDetail.getItems().setAll(rows);
            tableDetail.setVisible(true);
            tableDetail.setManaged(true);
            tableCurrentAccount.setVisible(false);
            tableCurrentAccount.setManaged(false);
            tableByDay.setVisible(false);
            tableByDay.setManaged(false);
            tableProducts.setVisible(false);
            tableProducts.setManaged(false);
            showProfitPanel(false);
            long ventas = rows.stream().mapToInt(SaleDetailRow::getSaleId).distinct().count();
            double ticketAverage = ventas == 0 ? 0 : totalDevengado / ventas;
            populateSummaryCards(totalDevengado, totalCobrado, totalVentasCC, totalPagosCC,
                    "Ventas registradas", String.valueOf(ventas),
                    "Ticket promedio", formatCurrency(ticketAverage));
            setSummaryVisible(true);
        } else {
            if (RENTABILIDAD.equals(type)) {
                showProfitPanel(true);
                setSummaryVisible(false);
                List<SalesByDay> dailyRows = reportService.getSalesByDayInRange(fromStr, toStr);
                updateProfitPanel(totalDevengado, totalCobrado, totalExpenses, netoDevengado, netoCaja, totalVentasCC, totalPagosCC, dailyRows);
                tableCurrentAccount.setVisible(false);
                tableCurrentAccount.setManaged(false);
            } else {
                showProfitPanel(false);
                tableByDay.setVisible(false);
                tableByDay.setManaged(false);
                tableProducts.setVisible(false);
                tableProducts.setManaged(false);
                tableDetail.setVisible(false);
                tableDetail.setManaged(false);
                tableCurrentAccount.setVisible(true);
                tableCurrentAccount.setManaged(true);

                List<CustomerCurrentAccountReportRow> ccRows = reportService.getCurrentAccountReportByCustomer(fromStr, toStr);
                tableCurrentAccount.getItems().setAll(ccRows);

                double ccSales = ccRows.stream().mapToDouble(CustomerCurrentAccountReportRow::getPeriodSales).sum();
                double ccPayments = ccRows.stream().mapToDouble(CustomerCurrentAccountReportRow::getPeriodPayments).sum();
                double finalDebt = ccRows.stream().mapToDouble(CustomerCurrentAccountReportRow::getFinalDebt).sum();
                double totalCurrentBalance = currentAccountService.getTotalCurrentBalance();
                populateCCSummaryCards(ccSales, ccPayments, finalDebt, totalCurrentBalance, ccRows.size());
                setSummaryVisible(true);
            }
        }
        updateDeleteButtonState();
    }

    private void setSummaryVisible(boolean visible) {
        summaryCards.setVisible(visible);
        summaryCards.setManaged(visible);
    }

    private void populateSummaryCards(double totalVendido, double totalCobrado,
                                      double totalVentasCC, double totalPagosCC,
                                      String extra1Title, String extra1Val,
                                      String extra2Title, String extra2Val) {
        sCard1Title.setText("Total vendido");    sCard1Value.setText(formatCurrency(totalVendido));
        sCard2Title.setText("Cobrado (caja)");   sCard2Value.setText(formatCurrency(totalCobrado));
        sCard3Title.setText("Ventas CC");        sCard3Value.setText(formatCurrency(totalVentasCC));
        sCard4Title.setText("Pagos CC");         sCard4Value.setText(formatCurrency(totalPagosCC));
        sCard5Title.setText(extra1Title);        sCard5Value.setText(extra1Val);
        sCard6Title.setText(extra2Title);        sCard6Value.setText(extra2Val);
        sCard6.setVisible(true);
        sCard6.setManaged(true);
    }

    private void populateCCSummaryCards(double ccSales, double ccPayments,
                                        double finalDebt, double totalCurrentBalance,
                                        int customerCount) {
        sCard1Title.setText("Ventas CC");        sCard1Value.setText(formatCurrency(ccSales));
        sCard2Title.setText("Pagos CC");         sCard2Value.setText(formatCurrency(ccPayments));

        if (totalCurrentBalance < -0.005) {
            sCard3Title.setText("Total a favor (clientes)");
            sCard3Value.setText(formatCurrency(-totalCurrentBalance));
            sCard3Value.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else if (totalCurrentBalance > 0.005) {
            sCard3Title.setText("Total adeudado (todos)");
            sCard3Value.setText(formatCurrency(totalCurrentBalance));
            sCard3Value.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        } else {
            sCard3Title.setText("Total adeudado (todos)");
            sCard3Value.setText(formatCurrency(0));
            sCard3Value.setStyle("");
        }

        if (finalDebt < -0.005) {
            sCard4Title.setText("Saldo a favor (período)");
            sCard4Value.setText(formatCurrency(-finalDebt));
            sCard4Value.setStyle("-fx-text-fill: #16a34a;");
        } else if (finalDebt > 0.005) {
            sCard4Title.setText("Deuda del período");
            sCard4Value.setText(formatCurrency(finalDebt));
            sCard4Value.setStyle("");
        } else {
            sCard4Title.setText("Deuda del período");
            sCard4Value.setText(formatCurrency(0));
            sCard4Value.setStyle("");
        }

        sCard5Title.setText("Clientes");         sCard5Value.setText(String.valueOf(customerCount));
        sCard6.setVisible(false);
        sCard6.setManaged(false);
    }

    private void showProfitPanel(boolean show) {
        if (profitPanel == null) return;
        profitPanel.setVisible(show);
        profitPanel.setManaged(show);
        if (show) {
            tableByDay.setVisible(false);
            tableByDay.setManaged(false);
            tableProducts.setVisible(false);
            tableProducts.setManaged(false);
            tableDetail.setVisible(false);
            tableDetail.setManaged(false);
        }
    }

    private void updateProfitPanel(double devengado, double cobrado, double gastos,
                                    double netoDev, double netoCaja,
                                    double ccSales, double ccPayments,
                                    List<SalesByDay> dailyRows) {
        if (profitPanel == null) return;
        profitDevengadoLabel.setText(formatCurrency(devengado));
        profitCobradoLabel.setText(formatCurrency(cobrado));
        profitGastosLabel.setText(formatCurrency(gastos));
        profitNetoCajaLabel.setText(formatCurrency(netoCaja));
        if (profitCcSalesLabel != null) profitCcSalesLabel.setText(formatCurrency(ccSales));
        if (profitCcPaymentsLabel != null) profitCcPaymentsLabel.setText(formatCurrency(ccPayments));

        // Narrative
        double contadoSales = devengado - ccSales;
        double pendienteCC  = Math.max(0, ccSales - ccPayments);

        if (devengado < 0.005) {
            profitLine1.setText("No se registraron ventas en el período.");
        } else if (ccSales < 0.005) {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total, todo cobrado al contado.");
        } else if (contadoSales < 0.005) {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total, todo en cuenta corriente (fiado).");
        } else {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total: "
                    + formatCurrency(contadoSales) + " al contado y "
                    + formatCurrency(ccSales) + " en cuenta corriente (fiado).");
        }

        if (ccSales > 0.005) {
            if (pendienteCC < 0.005) {
                profitLine2.setText("Las ventas CC del período están totalmente cobradas (" + formatCurrency(ccPayments) + " recibidos).");
            } else {
                profitLine2.setText("De las ventas CC cobraste " + formatCurrency(ccPayments)
                        + " en el período — quedan " + formatCurrency(pendienteCC) + " pendientes de cobro.");
            }
        } else {
            profitLine2.setText("");
        }

        if (gastos < 0.005) {
            profitLine3.setText("No se registraron gastos en el período. Ingresos de caja: " + formatCurrency(cobrado) + ".");
        } else {
            profitLine3.setText("Gastos del período: " + formatCurrency(gastos)
                    + ".  Ingresos de caja: " + formatCurrency(cobrado) + ".");
        }

        if (netoCaja >= 0) {
            profitLine4.setText("Resultado neto de caja: " + formatCurrency(netoCaja) + " — ganancia del período.");
            profitLine4.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else {
            profitLine4.setText("Resultado neto de caja: " + formatCurrency(netoCaja)
                    + " — los gastos superan los ingresos en " + formatCurrency(Math.abs(netoCaja)) + ".");
            profitLine4.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        }

        // Line chart evolution
        profitLineChart.getData().clear();
        if (!dailyRows.isEmpty()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
            for (SalesByDay row : dailyRows) {
                String label;
                try { label = LocalDate.parse(row.getDay()).format(fmt); }
                catch (Exception e) { label = row.getDay(); }
                series.getData().add(new XYChart.Data<>(label, row.getTotal()));
            }
            profitLineChart.getData().add(series);
        }
    }


    private String getTitleForType(String type) {
        return switch (type) {
            case VENTAS_POR_DIA -> "Ventas por Día";
            case VENTAS_CON_DETALLE -> "Ventas con Detalle";
            case PRODUCTOS_MAS_VENDIDOS -> "Productos Más Vendidos";
            case RENTABILIDAD -> "Rentabilidad";
            case REPORTE_CC -> "Cuenta Corriente";
            default -> "Reportes";
        };
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
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Error al anular venta");
            err.setHeaderText("No se pudo anular la venta Nº " + saleId);
            err.setContentText(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            err.getDialogPane().setMinWidth(480);
            err.setResizable(true);
            err.showAndWait();
        }
    }

    private static String formatCurrency(double total) {
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

    private static String formatQuantity(double quantity) {
        if (Math.abs(quantity - Math.rint(quantity)) < 0.000001d) {
            return String.format("%.0f", quantity);
        }
        return String.format("%.2f", quantity).replace('.', ',');
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }

    private void exportCustomerPdf(CustomerCurrentAccountReportRow row) {
        customerService.findById(row.getCustomerId()).ifPresentOrElse(customer -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar comprobante");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            String safeName = row.getCustomerName() == null ? "cliente"
                    : row.getCustomerName().replaceAll("[^a-zA-Z0-9-_ ]", "_");
            chooser.setInitialFileName("cuenta-corriente-" + safeName + ".pdf");
            java.io.File file = chooser.showSaveDialog(reportsRoot.getScene().getWindow());
            if (file == null) return;
            try {
                List<CustomerDebtRow> debts = currentAccountService.getSalesDebtRows(customer.getId());
                double balance = currentAccountService.getCustomerBalance(customer.getId());
                List<Integer> saleIds = new ArrayList<>();
                for (CustomerDebtRow debt : debts) {
                    if (debt.getPendingAmount() > 0.000001d) saleIds.add(debt.getSaleId());
                }
                Map<Integer, String> saleDetails = currentAccountService.getSaleDetailsBySaleIds(saleIds);
                com.ferreteria.models.AppSettings settings = new com.ferreteria.services.AppSettingsService().load();
                CurrentAccountPdfExporter.export(Path.of(file.getAbsolutePath()), customer, balance, debts, saleDetails, settings);
                showInfo("PDF exportado correctamente.");
            } catch (Exception e) {
                showError("No se pudo exportar el PDF: " + e.getMessage());
            }
        }, () -> showError("No se encontró el cliente."));
    }
}


