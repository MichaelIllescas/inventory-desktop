package com.ferreteria.controllers;

import com.ferreteria.models.Customer;
import com.ferreteria.models.CustomerDebtRow;
import com.ferreteria.models.Product;
import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.models.CustomerCurrentAccountReportRow;
import com.ferreteria.repositories.sqlite.SQLiteCustomerRepository;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;
import com.ferreteria.services.CurrentAccountService;
import com.ferreteria.services.CustomerService;
import com.ferreteria.services.LicenseService;
import com.ferreteria.services.TicketService;
import com.ferreteria.services.ReportService;
import com.ferreteria.services.SaleService;
import com.ferreteria.util.CurrentAccountPdfExporter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import static javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportsController {

    private static final String VENTAS_POR_DIA = "Por dia";
    private static final String PRODUCTOS_MAS_VENDIDOS = "Productos mas vendidos";
    private static final String VENTAS_CON_DETALLE = "Historial";
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
    private TableColumn<SaleDetailRow, String> colDetailSummary;
    @FXML
    private TableColumn<SaleDetailRow, Number> colDetailSaleTotal;
    @FXML
    private TableColumn<SaleDetailRow, String> colDetailPayment;
    @FXML
    private TableColumn<SaleDetailRow, Void> colDetailActions;
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
    private HBox profitCcRow;
    @FXML
    private VBox profitCobradoCard;
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
    private final LicenseService licenseService = new LicenseService();
    private final TicketService ticketService = new TicketService();
    private final Map<Integer, List<SaleDetailRow>> saleDetailsBySaleId = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        tableByDay.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableProducts.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableDetail.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableCurrentAccount.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        if (licenseService.isCurrentAccountsEnabled()) {
            reportTypeCombo.getItems().setAll(VENTAS_POR_DIA, VENTAS_CON_DETALLE, PRODUCTOS_MAS_VENDIDOS, RENTABILIDAD, REPORTE_CC);
        } else {
            reportTypeCombo.getItems().setAll(VENTAS_POR_DIA, VENTAS_CON_DETALLE, PRODUCTOS_MAS_VENDIDOS, RENTABILIDAD);
        }
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
                setAlignment(javafx.geometry.Pos.CENTER);
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
        colDayCurrentAccount.setVisible(licenseService.isCurrentAccountsEnabled());
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
        colDetailSummary.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getProductName()));
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
        if (colDetailActions != null) {
            colDetailActions.setCellFactory(tc -> new TableCell<>() {
                private final Button detailButton = new Button("Ver detalle");
                private final Button editButton = new Button("Editar");
                private final Button ticketButton = new Button("Ticket");
                private final HBox box = new HBox(6, detailButton, editButton, ticketButton);
                {
                    detailButton.getStyleClass().add("report-detail-button");
                    editButton.getStyleClass().add("report-edit-button");
                    ticketButton.getStyleClass().add("report-detail-button");
                    // Sin esto la politica de resize de la tabla achica la columna
                    // y los botones truncan su texto a "Ver det...", "Ed...", "Tic...".
                    for (Button b : List.of(detailButton, editButton, ticketButton)) {
                        b.setMinWidth(Region.USE_PREF_SIZE);
                    }
                    box.setMinWidth(Region.USE_PREF_SIZE);
                    box.setAlignment(javafx.geometry.Pos.CENTER);
                    ticketButton.setOnAction(e -> {
                        SaleDetailRow row = getTableView().getItems().get(getIndex());
                        printTicket(row.getSaleId());
                    });
                    detailButton.setOnAction(e -> {
                        SaleDetailRow row = getTableView().getItems().get(getIndex());
                        showSaleDetailDialog(row.getSaleId());
                    });
                    editButton.setOnAction(e -> {
                        SaleDetailRow row = getTableView().getItems().get(getIndex());
                        showEditSaleDialog(row.getSaleId());
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : box);
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            });
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
                private final Button pdfBtn = new Button("↓ PDF");
                private final Button manageBtn = new Button("Gestionar");
                private final HBox box = new HBox(6, pdfBtn, manageBtn);
                {
                    pdfBtn.getStyleClass().add("cc-export-btn");
                    manageBtn.getStyleClass().add("cc-manage-btn");
                    pdfBtn.setOnAction(e -> {
                        CustomerCurrentAccountReportRow row = getTableView().getItems().get(getIndex());
                        exportCustomerPdf(row);
                    });
                    manageBtn.setOnAction(e -> {
                        CustomerCurrentAccountReportRow row = getTableView().getItems().get(getIndex());
                        manageCurrentAccount(row);
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : box);
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
        if (REPORTE_CC.equals(type) && !licenseService.isCurrentAccountsEnabled()) {
            showError("El módulo de cuentas corrientes no está habilitado en esta edición.");
            reportTypeCombo.getSelectionModel().select(VENTAS_POR_DIA);
            return;
        }

        double totalExpenses = expenseRepo.getTotalInRange(fromStr, toStr);
        double totalDevengado = reportService.getSalesTotalInRange(fromStr, toStr);
        double totalCobrado = reportService.getCollectedTotalInRange(fromStr, toStr);
        boolean currentAccountsEnabled = licenseService.isCurrentAccountsEnabled();
        double totalVentasCC = currentAccountsEnabled ? reportService.getCurrentAccountSalesTotalInRange(fromStr, toStr) : 0;
        double totalPagosCC = currentAccountsEnabled ? reportService.getCurrentAccountPaymentsTotalInRange(fromStr, toStr) : 0;
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
            tableDetail.getItems().setAll(groupSaleDetails(rows));
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
        sCard2Title.setText("Cobrado");   sCard2Value.setText(formatCurrency(totalCobrado));
        // Sin cuentas corrientes no hay ventas fiadas, asi que lo cobrado es siempre
        // igual a lo vendido: la tarjeta repetiria el numero de al lado.
        setCardVisible(sCard2, licenseService.isCurrentAccountsEnabled());
        if (licenseService.isCurrentAccountsEnabled()) {
            sCard3Title.setText("Ventas CC");        sCard3Value.setText(formatCurrency(totalVentasCC));
            sCard4Title.setText("Pagos CC");         sCard4Value.setText(formatCurrency(totalPagosCC));
            setCardVisible(sCard3, true);
            setCardVisible(sCard4, true);
        } else {
            setCardVisible(sCard3, false);
            setCardVisible(sCard4, false);
        }
        sCard5Title.setText(extra1Title);        sCard5Value.setText(extra1Val);
        sCard6Title.setText(extra2Title);        sCard6Value.setText(extra2Val);
        sCard6.setVisible(true);
        sCard6.setManaged(true);
    }

    private void setCardVisible(VBox card, boolean visible) {
        if (card == null) return;
        card.setVisible(visible);
        card.setManaged(visible);
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

    /** Reimprime el ticket de una venta ya registrada. */
    private void printTicket(int saleId) {
        List<SaleDetailRow> rows = saleDetailsBySaleId.get(saleId);
        if (rows == null || rows.isEmpty()) {
            showError("No se encontraron los items de la venta " + saleId + ".");
            return;
        }
        try {
            ticketService.print(ticketService.fromHistory(rows, ticketService.findCustomerName(saleId)));
        } catch (Exception e) {
            com.ferreteria.util.AppLogger.error("ReportsController", "printTicket",
                    "Error al reimprimir ticket de la venta " + saleId, e);
            showError("No se pudo imprimir el ticket."
                    + System.lineSeparator()
                    + "Revisá la impresora configurada en Configuración.");
        }
    }

    private List<SaleDetailRow> groupSaleDetails(List<SaleDetailRow> rows) {
        saleDetailsBySaleId.clear();
        for (SaleDetailRow row : rows) {
            saleDetailsBySaleId.computeIfAbsent(row.getSaleId(), ignored -> new ArrayList<>()).add(row);
        }

        List<SaleDetailRow> groupedRows = new ArrayList<>();
        for (Map.Entry<Integer, List<SaleDetailRow>> entry : saleDetailsBySaleId.entrySet()) {
            List<SaleDetailRow> details = entry.getValue();
            if (details.isEmpty()) {
                continue;
            }
            SaleDetailRow first = details.get(0);
            double totalUnits = details.stream().mapToDouble(SaleDetailRow::getQuantity).sum();
            String summary = details.size() + " producto" + (details.size() == 1 ? "" : "s")
                    + " / " + formatQuantity(totalUnits) + " unidad" + (Math.abs(totalUnits - 1d) < 0.000001d ? "" : "es");
            groupedRows.add(new SaleDetailRow(
                    first.getSaleId(),
                    first.getSaleDate(),
                    "",
                    summary,
                    totalUnits,
                    0,
                    details.stream().mapToDouble(SaleDetailRow::getSubtotal).sum(),
                    first.getSaleTotal(),
                    first.getPaymentMethod()
            ));
        }
        return groupedRows;
    }

    private void showSaleDetailDialog(int saleId) {
        List<SaleDetailRow> details = saleDetailsBySaleId.getOrDefault(saleId, List.of());
        if (details.isEmpty()) {
            return;
        }

        SaleDetailDialog.show(tableDetail.getScene() != null ? tableDetail.getScene().getWindow() : null,
                saleId, details);
    }

    private void showEditSaleDialog(int saleId) {
        List<SaleDetailRow> originalDetails = saleDetailsBySaleId.getOrDefault(saleId, List.of());
        if (originalDetails.isEmpty()) {
            return;
        }

        List<SaleDetailRow> editableDetails = originalDetails.stream()
                .map(row -> new SaleDetailRow(
                        row.getSaleId(),
                        row.getProductId(),
                        row.getSaleDate(),
                        row.getProductCode(),
                        row.getProductName(),
                        row.getQuantity(),
                        row.getUnitPrice(),
                        row.getSubtotal(),
                        row.getSaleTotal(),
                        row.getPaymentMethod()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        SaleDetailRow first = editableDetails.get(0);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar venta");
        dialog.setHeaderText("Venta Nro " + saleId);
        ButtonType saveButton = new ButtonType("Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        DatePicker saleDatePicker = new DatePicker(resolveSaleDate(first.getSaleDate()));
        ComboBox<String> saleTimeCombo = new ComboBox<>();
        saleTimeCombo.getItems().setAll(buildTimeSlots());
        saleTimeCombo.getSelectionModel().select(resolveSaleTime(first.getSaleDate()));

        HBox dateRow = new HBox(8,
                new Label("Fecha:"), saleDatePicker,
                new Label("Hora:"), saleTimeCombo
        );
        dateRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // El total real de la venta puede incluir un descuento/ajuste aplicado al momento de venderla,
        // por eso no se recalcula ciegamente desde los ítems.
        Runnable[] refreshTotals = new Runnable[1];

        TableView<SaleDetailRow> editTable = new TableView<>();
        editTable.setEditable(true);
        editTable.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        editTable.setPrefSize(820, 320);

        TableColumn<SaleDetailRow, String> productCol = new TableColumn<>("Producto");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productName"));
        productCol.setPrefWidth(260);

        StringConverter<Double> doubleConverter = new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : String.format("%.2f", value).replace('.', ',');
            }

            @Override
            public Double fromString(String value) {
                if (value == null || value.isBlank()) return null;
                try {
                    return Double.parseDouble(value.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };

        TableColumn<SaleDetailRow, Double> qtyCol = new TableColumn<>("Cantidad");
        qtyCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getQuantity()).asObject());
        qtyCol.setCellFactory(col -> new javafx.scene.control.cell.TextFieldTableCell<>(doubleConverter));
        qtyCol.setOnEditCommit(e -> {
            Double value = e.getNewValue();
            if (value != null && value > 0) {
                e.getRowValue().setQuantity(value);
                e.getRowValue().setSubtotal(value * e.getRowValue().getUnitPrice());
                editTable.refresh();
                refreshTotals[0].run();
            }
        });

        TableColumn<SaleDetailRow, Double> priceCol = new TableColumn<>("Precio unit.");
        priceCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getUnitPrice()).asObject());
        priceCol.setCellFactory(col -> new javafx.scene.control.cell.TextFieldTableCell<>(doubleConverter));
        priceCol.setOnEditCommit(e -> {
            Double value = e.getNewValue();
            if (value != null && value >= 0) {
                e.getRowValue().setUnitPrice(value);
                e.getRowValue().setSubtotal(value * e.getRowValue().getQuantity());
                editTable.refresh();
                refreshTotals[0].run();
            }
        });

        TableColumn<SaleDetailRow, Number> subtotalCol = new TableColumn<>("Subtotal");
        subtotalCol.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        subtotalCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });

        TableColumn<SaleDetailRow, Void> removeCol = new TableColumn<>("Quitar");
        removeCol.setCellFactory(tc -> new TableCell<>() {
            private final Button removeButton = new Button("Quitar");
            {
                removeButton.getStyleClass().add("report-delete-button");
                removeButton.setOnAction(e -> {
                    SaleDetailRow row = getTableView().getItems().get(getIndex());
                    getTableView().getItems().remove(row);
                    refreshTotals[0].run();
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeButton);
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });

        editTable.getColumns().setAll(productCol, qtyCol, priceCol, subtotalCol, removeCol);
        editTable.getItems().setAll(editableDetails);

        Button addProductButton = new Button("+ Añadir producto");
        addProductButton.getStyleClass().add("report-detail-button");
        addProductButton.setOnAction(e -> {
            Product product = ProductSelectionDialog.show(dialog.getDialogPane().getScene().getWindow());
            if (product == null) {
                return;
            }
            SaleDetailRow existing = editTable.getItems().stream()
                    .filter(row -> row.getProductId() == product.getId())
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + 1);
                existing.setSubtotal(existing.getQuantity() * existing.getUnitPrice());
                editTable.refresh();
                refreshTotals[0].run();
                return;
            }
            editTable.getItems().add(new SaleDetailRow(
                    saleId,
                    product.getId(),
                    first.getSaleDate(),
                    product.getCode(),
                    product.getName(),
                    1,
                    product.getPrice(),
                    product.getPrice(),
                    first.getSaleTotal(),
                    first.getPaymentMethod()
            ));
            refreshTotals[0].run();
        });

        // Total cobrado: arranca con el total real guardado (con su descuento/ajuste) y conserva
        // ese ajuste en pesos si se modifican los ítems. Es editable.
        double originalItemsSum = editableDetails.stream().mapToDouble(SaleDetailRow::getSubtotal).sum();
        double[] adjustment = { first.getSaleTotal() - originalItemsSum };

        Label itemsSumLabel = new Label();
        Label adjustmentLabel = new Label();
        TextField totalField = new TextField(formatAmount(first.getSaleTotal()));
        totalField.setPrefWidth(140);

        refreshTotals[0] = () -> {
            double itemsSum = editTable.getItems().stream().mapToDouble(SaleDetailRow::getSubtotal).sum();
            itemsSumLabel.setText("Suma de ítems: " + formatCurrency(itemsSum));
            if (!totalField.isFocused()) {
                totalField.setText(formatAmount(itemsSum + adjustment[0]));
            }
            double diff = parseAmount(totalField.getText(), itemsSum) - itemsSum;
            if (Math.abs(diff) > 0.005) {
                adjustmentLabel.setText((diff > 0 ? "Aumento: " : "Descuento: ") + formatCurrency(Math.abs(diff)));
                adjustmentLabel.setStyle(diff > 0
                        ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;"
                        : "-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            } else {
                adjustmentLabel.setText("");
            }
        };

        totalField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(isFocused)) {
                double itemsSum = editTable.getItems().stream().mapToDouble(SaleDetailRow::getSubtotal).sum();
                adjustment[0] = parseAmount(totalField.getText(), itemsSum) - itemsSum;
                refreshTotals[0].run();
            }
        });
        totalField.setOnAction(e -> totalField.getParent().requestFocus());

        HBox totalRow = new HBox(12, itemsSumLabel, new Label("Total cobrado ($):"), totalField, adjustmentLabel);
        totalRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        refreshTotals[0].run();

        VBox content = new VBox(10, dateRow, editTable, addProductButton, totalRow);
        content.setPadding(new javafx.geometry.Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(900);
        dialog.setResizable(true);

        Button saveNode = (Button) dialog.getDialogPane().lookupButton(saveButton);
        saveNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                if (editTable.getItems().isEmpty()) {
                    throw new IllegalArgumentException("La venta debe tener al menos un ítem.");
                }
                LocalDate date = saleDatePicker.getValue();
                String time = saleTimeCombo.getValue();
                if (date == null || time == null || time.isBlank()) {
                    throw new IllegalArgumentException("Debe indicar fecha y hora.");
                }
                LocalDateTime dateTime = LocalDateTime.of(date, LocalTime.parse(time));
                List<SaleItem> newItems = editTable.getItems().stream()
                        .map(row -> new SaleItem(saleId, row.getProductId(), row.getQuantity(), row.getUnitPrice()))
                        .toList();
                double itemsSum = editTable.getItems().stream().mapToDouble(SaleDetailRow::getSubtotal).sum();
                double finalTotal = parseAmount(totalField.getText(), itemsSum);
                if (finalTotal < 0) {
                    throw new IllegalArgumentException("El total cobrado no puede ser negativo.");
                }
                saleService.updateSale(saleId, dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), newItems, finalTotal);
                onGenerate();
            } catch (Exception ex) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("No se pudo editar la venta");
                alert.setHeaderText("Revisá los datos ingresados");
                alert.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                alert.getDialogPane().setMinWidth(460);
                alert.showAndWait();
            }
        });

        dialog.showAndWait();
    }

    private LocalDate resolveSaleDate(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate();
        } catch (Exception e) {
            try {
                return LocalDate.parse(value);
            } catch (Exception ignored) {
                return LocalDate.now();
            }
        }
    }

    private String resolveSaleTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "00:00";
        }
    }

    private String formatSaleDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return value;
        }
    }

    private String safePayment(String paymentMethod) {
        return paymentMethod == null || paymentMethod.isBlank() ? "-" : paymentMethod;
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
        boolean ccEnabled = licenseService.isCurrentAccountsEnabled();
        if (profitCcRow != null) {
            profitCcRow.setVisible(ccEnabled);
            profitCcRow.setManaged(ccEnabled);
        }
        // Mismo motivo que en las tarjetas de resumen: sin fiado, cobrado == vendido.
        if (profitCobradoCard != null) {
            profitCobradoCard.setVisible(ccEnabled);
            profitCobradoCard.setManaged(ccEnabled);
        }

        // Narrative
        double contadoSales  = devengado - ccSales;

        // Sum payment methods from daily rows (sales + CC payments merged, excludes CC sales)
        double totalCash     = dailyRows.stream().mapToDouble(SalesByDay::getCash).sum();
        double totalTransfer = dailyRows.stream().mapToDouble(SalesByDay::getTransfer).sum();
        double totalDebit    = dailyRows.stream().mapToDouble(SalesByDay::getDebit).sum();
        double totalCredit   = dailyRows.stream().mapToDouble(SalesByDay::getCredit).sum();

        // Line 1: sales only — contado vs CC
        if (devengado < 0.005) {
            profitLine1.setText("No se registraron ventas en el período.");
        } else if (!ccEnabled) {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total.");
        } else if (ccSales < 0.005) {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total, todo cobrado al contado.");
        } else if (contadoSales < 0.005) {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total, todo en cuenta corriente (fiado).");
        } else {
            profitLine1.setText("Vendiste " + formatCurrency(devengado) + " en total: "
                    + formatCurrency(contadoSales) + " al contado y "
                    + formatCurrency(ccSales) + " en cuenta corriente (fiado).");
        }

        // Line 2: CC payments received (simplified, no claim about whether debt is fully covered)
        if (ccPayments > 0.005) {
            profitLine2.setText("Se cobraron " + formatCurrency(ccPayments) + " de deudas de cuenta corriente en el período.");
            profitLine2.setVisible(true);
            profitLine2.setManaged(true);
        } else {
            profitLine2.setText("");
            profitLine2.setVisible(false);
            profitLine2.setManaged(false);
        }

        // Line 3: total cobrado with method breakdown + gastos
        ArrayList<String> cobradoParts = new ArrayList<>();
        if (totalCash     > 0.005) cobradoParts.add(formatCurrency(totalCash)     + " efectivo");
        if (totalTransfer > 0.005) cobradoParts.add(formatCurrency(totalTransfer) + " transferencia");
        if (totalDebit    > 0.005) cobradoParts.add(formatCurrency(totalDebit)    + " débito");
        if (totalCredit   > 0.005) cobradoParts.add(formatCurrency(totalCredit)   + " crédito");

        String cobradoStr = cobradoParts.isEmpty()
                ? formatCurrency(cobrado)
                : formatCurrency(cobrado) + " (" + String.join(", ", cobradoParts) + ")";

        if (gastos < 0.005) {
            profitLine3.setText("Total cobrado: " + cobradoStr + ".");
        } else {
            profitLine3.setText("Total cobrado: " + cobradoStr + ".  Gastos: " + formatCurrency(gastos) + ".");
        }

        // Line 4: resultado neto
        if (netoCaja >= 0) {
            profitLine4.setText("Resultado neto: " + formatCurrency(netoCaja) + " — ganancia del período.");
            profitLine4.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else {
            profitLine4.setText("Resultado neto: " + formatCurrency(netoCaja)
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
            case VENTAS_CON_DETALLE -> "Historial de ventas";
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

    /** Monto sin símbolo, apto para campos editables ("1.234,56"). */
    private static String formatAmount(double value) {
        return formatCurrency(value).replace("$ ", "");
    }

    /** Acepta "1.234,56" (coma decimal) y "1234.56" (punto decimal). */
    private static double parseAmount(String text, double fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            String clean = text.trim().replace("$", "").trim();
            if (clean.contains(",")) {
                clean = clean.replace(".", "").replace(',', '.');
            }
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return fallback;
        }
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

    private void manageCurrentAccount(CustomerCurrentAccountReportRow row) {
        MainController mainController = MainController.getInstance();
        if (mainController == null) {
            showError("No se pudo abrir la cuenta corriente.");
            return;
        }
        Object controller = mainController.openSection("current-account-view.fxml");
        if (controller instanceof CurrentAccountController currentAccountController) {
            currentAccountController.selectCustomer(row.getCustomerId());
        } else {
            showError("El módulo de cuentas corrientes no está disponible.");
        }
    }
}


