package com.ferreteria.controllers;

import com.ferreteria.models.Customer;
import com.ferreteria.models.CustomerAccountMovementRow;
import com.ferreteria.models.CustomerDebtRow;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.repositories.sqlite.SQLiteCustomerRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;
import com.ferreteria.services.CurrentAccountService;
import com.ferreteria.services.CustomerService;
import com.ferreteria.util.CurrentAccountPdfExporter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CurrentAccountController {
    private static final DateTimeFormatter UI_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final double BREAKPOINT_COMPACT = 1050;
    private static final double BREAKPOINT_XCOMPACT = 780;
    private static final String STYLE_COMPACT = "ca-compact";
    private static final String STYLE_XCOMPACT = "ca-xcompact";

    @FXML
    private BorderPane rootPane;

    @FXML
    private TextField searchCustomerField;
    @FXML
    private ComboBox<Customer> customerCombo;
    @FXML
    private Label summaryBalanceLabel;
    @FXML
    private Label summaryPendingSalesLabel;
    @FXML
    private Label customerNameLabel;
    @FXML
    private Label customerPhoneLabel;
    @FXML
    private Label customerAddressLabel;
    @FXML
    private Label customerLimitLabel;
    @FXML
    private Label customerAvailableLabel;
    @FXML
    private TableView<CustomerDebtRow> debtTable;
    @FXML
    private TableColumn<CustomerDebtRow, Number> colDebtSaleId;
    @FXML
    private TableColumn<CustomerDebtRow, String> colDebtDate;
    @FXML
    private TableColumn<CustomerDebtRow, Number> colDebtTotal;
    @FXML
    private TableColumn<CustomerDebtRow, Number> colDebtApplied;
    @FXML
    private TableColumn<CustomerDebtRow, Number> colDebtPending;
    @FXML
    private TableColumn<CustomerDebtRow, String> colDebtStatus;
    @FXML
    private TableView<CustomerAccountMovementRow> movementTable;
    @FXML
    private TableColumn<CustomerAccountMovementRow, String> colMovDate;
    @FXML
    private TableColumn<CustomerAccountMovementRow, String> colMovType;
    @FXML
    private TableColumn<CustomerAccountMovementRow, Number> colMovAmount;
    @FXML
    private TableColumn<CustomerAccountMovementRow, Number> colMovBalance;
    @FXML
    private TableColumn<CustomerAccountMovementRow, String> colMovRef;
    @FXML
    private TableColumn<CustomerAccountMovementRow, String> colMovNotes;
    @FXML
    private TextField paymentAmountField;
    @FXML
    private ComboBox<String> paymentMethodCombo;
    @FXML
    private TextField paymentNotesField;
    @FXML
    private TextField initialDebtAmountField;
    @FXML
    private TextField initialDebtNotesField;
    @FXML
    private Button registerPaymentButton;
    @FXML
    private Button registerInitialDebtButton;
    @FXML
    private Button exportPdfButton;
    @FXML
    private Button deleteMovementButton;

    private final CustomerService customerService;
    private final CurrentAccountService currentAccountService;
    private final SQLiteSaleRepository saleRepository;
    private final ObservableList<CustomerDebtRow> debtRows = FXCollections.observableArrayList();
    private final ObservableList<CustomerAccountMovementRow> movementRows = FXCollections.observableArrayList();

    public CurrentAccountController() {
        this.customerService = new CustomerService(new SQLiteCustomerRepository());
        this.currentAccountService = new CurrentAccountService();
        this.saleRepository = new SQLiteSaleRepository();
    }

    @FXML
    public void initialize() {
        setupCustomerCombo();
        setupDebtTable();
        setupMovementTable();
        setupPaymentControls();
        setupResponsiveMode();
        reloadCustomers();
        clearSummary();
        clearCustomerPreview();
        updatePaymentButtonState();
    }

    public void selectCustomer(int customerId) {
        customerCombo.getItems().stream()
                .filter(c -> c.getId() != null && c.getId() == customerId)
                .findFirst()
                .ifPresentOrElse(
                        c -> customerCombo.getSelectionModel().select(c),
                        () -> customerService.findById(customerId).ifPresent(c -> {
                            customerCombo.getItems().add(c);
                            customerCombo.getSelectionModel().select(c);
                        }));
    }

    private void setupResponsiveMode() {
        if (rootPane == null) {
            return;
        }
        rootPane.widthProperty().addListener((obs, old, w) -> applyResponsiveMode(w.doubleValue()));
    }

    private void applyResponsiveMode(double width) {
        if (rootPane == null) {
            return;
        }
        rootPane.getStyleClass().removeAll(STYLE_COMPACT, STYLE_XCOMPACT);
        if (width < BREAKPOINT_XCOMPACT) {
            rootPane.getStyleClass().addAll(STYLE_COMPACT, STYLE_XCOMPACT);
        } else if (width < BREAKPOINT_COMPACT) {
            rootPane.getStyleClass().add(STYLE_COMPACT);
        }
    }

    private void setupCustomerCombo() {
        customerCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                return customer == null ? "" : customer.getName();
            }

            @Override
            public Customer fromString(String string) {
                return null;
            }
        });
        customerCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updatePaymentButtonState();
            loadCustomerAccountData(newVal);
        });
        if (searchCustomerField != null) {
            searchCustomerField.setOnAction(e -> onSearchCustomer());
        }
    }

    private void setupDebtTable() {
        colDebtSaleId.setCellValueFactory(new PropertyValueFactory<>("saleId"));
        colDebtDate.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        colDebtDate.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatDateTime(item));
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        colDebtTotal.setCellValueFactory(new PropertyValueFactory<>("saleTotal"));
        colDebtApplied.setCellValueFactory(new PropertyValueFactory<>("appliedAmount"));
        colDebtPending.setCellValueFactory(new PropertyValueFactory<>("pendingAmount"));
        colDebtStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        for (TableColumn<CustomerDebtRow, Number> col : List.of(colDebtTotal, colDebtApplied, colDebtPending)) {
            col.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(Number item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            });
        }
        for (TableColumn<CustomerDebtRow, ?> col : List.of(colDebtSaleId, colDebtDate, colDebtTotal, colDebtApplied, colDebtPending, colDebtStatus)) {
            col.setStyle("-fx-alignment: CENTER;");
        }
        debtTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        debtTable.setItems(debtRows);

        // Doble clic sobre una venta: muestra qué productos compró el cliente esa vez.
        debtTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<CustomerDebtRow> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSaleDetail(row.getItem().getSaleId());
                }
            });
            row.setCursor(javafx.scene.Cursor.HAND);
            return row;
        });
    }

    private void showSaleDetail(int saleId) {
        try {
            List<SaleDetailRow> details = saleRepository.getSaleDetailsBySaleId(saleId);
            if (details.isEmpty()) {
                showInfo("No se encontraron productos para la venta " + saleId + ".");
                return;
            }
            SaleDetailDialog.show(debtTable.getScene() != null ? debtTable.getScene().getWindow() : null,
                    saleId, details);
        } catch (Exception e) {
            showError("No se pudo abrir el detalle de la venta " + saleId + ": "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private void setupMovementTable() {
        colMovDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colMovDate.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatDateTime(item));
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        colMovType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colMovAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colMovBalance.setCellValueFactory(new PropertyValueFactory<>("runningBalance"));
        colMovRef.setCellValueFactory(cell -> {
            CustomerAccountMovementRow row = cell.getValue();
            String ref = row.getSaleId() != null
                    ? "Venta " + row.getSaleId()
                    : (row.getPaymentId() != null ? "Pago " + row.getPaymentId() : "Ajuste");
            return new javafx.beans.property.SimpleStringProperty(ref);
        });
        colMovNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));

        colMovAmount.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        colMovBalance.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(Math.abs(item.doubleValue())));
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        for (TableColumn<CustomerAccountMovementRow, ?> col : List.of(colMovDate, colMovType, colMovAmount, colMovBalance, colMovRef, colMovNotes)) {
            col.setStyle("-fx-alignment: CENTER;");
        }

        movementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movementTable.setItems(movementRows);
        movementTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean deletable = sel != null && (
                (sel.getSaleId() == null && sel.getPaymentId() == null) ||   // ajuste manual
                ("CREDITO".equals(sel.getType()) && sel.getPaymentId() != null) // pago reversible
            );
            if (deleteMovementButton != null) {
                deleteMovementButton.setDisable(!deletable);
                if (sel != null && "CREDITO".equals(sel.getType()) && sel.getPaymentId() != null) {
                    deleteMovementButton.setText("Eliminar pago");
                } else {
                    deleteMovementButton.setText("Eliminar ajuste");
                }
            }
        });
    }

    private void setupPaymentControls() {
        paymentMethodCombo.setItems(FXCollections.observableArrayList("Efectivo", "Transferencia", "Debito", "Credito"));
        paymentMethodCombo.getSelectionModel().selectFirst();
    }

    @FXML
    private void onSearchCustomer() {
        String query = searchCustomerField != null ? searchCustomerField.getText() : null;
        List<Customer> result = customerService.searchActiveCustomers(query);
        customerCombo.setItems(FXCollections.observableArrayList(result));
        if (result.size() == 1) {
            customerCombo.getSelectionModel().select(result.get(0));
        } else if (result.isEmpty()) {
            customerCombo.getSelectionModel().clearSelection();
            clearTablesAndSummary();
            showWarning("No se encontraron clientes.");
        } else {
            customerCombo.getSelectionModel().clearSelection();
            clearTablesAndSummary();
        }
    }

    @FXML
    private void onResetCustomerFilter() {
        if (searchCustomerField != null) {
            searchCustomerField.clear();
        }
        reloadCustomers();
        customerCombo.getSelectionModel().clearSelection();
        clearTablesAndSummary();
    }

    @FXML
    private void onRegisterPayment() {
        Customer customer = customerCombo.getSelectionModel().getSelectedItem();
        if (customer == null || customer.getId() == null) {
            showWarning("Seleccione un cliente.");
            return;
        }
        double amount;
        try {
            amount = parseAmount(paymentAmountField.getText());
        } catch (NumberFormatException e) {
            showWarning("Monto invalido.");
            return;
        }
        if (amount <= 0) {
            showWarning("El monto debe ser mayor a 0.");
            return;
        }
        String method = paymentMethodCombo.getSelectionModel().getSelectedItem();
        if (method == null || method.isBlank()) {
            method = "Efectivo";
        }
        String notes = paymentNotesField.getText();
        try {
            currentAccountService.registerPayment(customer.getId(), amount, method, notes);
            paymentAmountField.clear();
            paymentNotesField.clear();
            loadCustomerAccountData(customer);
            showInfo("Pago registrado correctamente.");
        } catch (Exception e) {
            showError("No se pudo registrar el pago: " + e.getMessage());
        }
    }

    @FXML
    private void onRegisterInitialDebt() {
        Customer customer = customerCombo.getSelectionModel().getSelectedItem();
        if (customer == null || customer.getId() == null) {
            showWarning("Seleccione un cliente.");
            return;
        }
        double amount;
        try {
            amount = parseAmount(initialDebtAmountField.getText());
        } catch (NumberFormatException e) {
            showWarning("Monto invalido.");
            return;
        }
        if (amount <= 0) {
            showWarning("El monto debe ser mayor a 0.");
            return;
        }
        String notes = initialDebtNotesField == null ? null : initialDebtNotesField.getText();
        try {
            currentAccountService.registerInitialDebt(customer.getId(), amount, notes);
            initialDebtAmountField.clear();
            if (initialDebtNotesField != null) {
                initialDebtNotesField.clear();
            }
            loadCustomerAccountData(customer);
            showInfo("Deuda anterior registrada correctamente.");
        } catch (Exception e) {
            showError("No se pudo registrar la deuda anterior: " + e.getMessage());
        }
    }

    @FXML
    private void onExportPdf() {
        Customer customer = customerCombo.getSelectionModel().getSelectedItem();
        if (customer == null || customer.getId() == null) {
            showWarning("Seleccione un cliente para exportar.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar resumen de cuenta");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        String safeName = customer.getName() == null ? "cliente" : customer.getName().replaceAll("[^a-zA-Z0-9-_ ]", "_");
        chooser.setInitialFileName("resumen-cuenta-" + safeName + ".pdf");
        java.io.File file = chooser.showSaveDialog(customerCombo.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            List<CustomerDebtRow> debts = currentAccountService.getSalesDebtRows(customer.getId());
            double currentBalance = currentAccountService.getCustomerBalance(customer.getId());
            List<Integer> saleIds = new ArrayList<>();
            for (CustomerDebtRow row : debts) {
                if (row.getPendingAmount() > 0.000001d) {
                    saleIds.add(row.getSaleId());
                }
            }
            Map<Integer, String> saleDetails = currentAccountService.getSaleDetailsBySaleIds(saleIds);
            com.ferreteria.models.AppSettings settings = new com.ferreteria.services.AppSettingsService().load();
            CurrentAccountPdfExporter.export(Path.of(file.getAbsolutePath()), customer, currentBalance, debts, saleDetails, settings);
            showInfo("PDF exportado correctamente.");
        } catch (Exception e) {
            showError("No se pudo exportar PDF: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteMovement() {
        CustomerAccountMovementRow selected = movementTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        boolean isPayment = "CREDITO".equals(selected.getType()) && selected.getPaymentId() != null;
        String title = isPayment ? "Eliminar pago" : "Eliminar ajuste";
        String body = isPayment
                ? "¿Eliminar el pago de " + formatCurrency(selected.getAmount()) + "?\n" +
                  "Esto revertirá el pago y las ventas asociadas volverán a quedar pendientes."
                : "¿Eliminar el ajuste de " + formatCurrency(selected.getAmount()) + "?\n\"" +
                  (selected.getNotes() != null ? selected.getNotes() : "") + "\"";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(title);
        confirm.setHeaderText(null);
        confirm.setContentText(body);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != javafx.scene.control.ButtonType.OK) return;
            try {
                if (isPayment) {
                    currentAccountService.deletePayment(selected.getPaymentId());
                } else {
                    currentAccountService.deleteManualMovement(selected.getId());
                }
                Customer customer = customerCombo.getSelectionModel().getSelectedItem();
                loadCustomerAccountData(customer);
                showInfo(isPayment ? "Pago eliminado correctamente." : "Ajuste eliminado correctamente.");
            } catch (Exception e) {
                showError("No se pudo eliminar: " + e.getMessage());
            }
        });
    }

    private void reloadCustomers() {
        customerCombo.setItems(FXCollections.observableArrayList(customerService.getAllActiveCustomers()));
    }

    private void loadCustomerAccountData(Customer customer) {
        if (customer == null || customer.getId() == null) {
            clearTablesAndSummary();
            return;
        }
        List<CustomerDebtRow> debts = currentAccountService.getSalesDebtRows(customer.getId());
        debtRows.setAll(debts);
        movementRows.setAll(currentAccountService.getMovements(customer.getId()));

        long pendingCount = debts.stream().filter(d -> d.getPendingAmount() > 0.000001d).count();
        double totalPending = debts.stream().mapToDouble(CustomerDebtRow::getPendingAmount).sum();
        double currentBalance = currentAccountService.getCustomerBalance(customer.getId());
        summaryBalanceLabel.setText(formatBalanceWithState(currentBalance));
        summaryPendingSalesLabel.setText(String.valueOf(pendingCount));
        updateCustomerPreview(customer, currentBalance);
    }

    private void clearTablesAndSummary() {
        debtRows.clear();
        movementRows.clear();
        clearSummary();
        clearCustomerPreview();
    }

    private void clearSummary() {
        summaryBalanceLabel.setText("Sin deuda");
        summaryPendingSalesLabel.setText("0");
    }

    private void updateCustomerPreview(Customer customer, double balance) {
        if (customer == null) {
            clearCustomerPreview();
            return;
        }
        customerNameLabel.setText(valueOrDash(customer.getName()));
        customerPhoneLabel.setText(valueOrDash(customer.getPhone()));
        customerAddressLabel.setText(valueOrDash(customer.getAddress()));
        customerLimitLabel.setText(formatCreditLimit(customer.getCreditLimit()));
        customerAvailableLabel.setText(formatAvailableCredit(customer.getCreditLimit(), balance));
    }

    private void clearCustomerPreview() {
        customerNameLabel.setText("-");
        customerPhoneLabel.setText("-");
        customerAddressLabel.setText("-");
        customerLimitLabel.setText("Sin limite");
        customerAvailableLabel.setText("-");
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private String formatCreditLimit(double value) {
        if (value < 0) {
            return "Sin limite";
        }
        return formatCurrency(value);
    }

    private String formatAvailableCredit(double creditLimit, double balance) {
        if (creditLimit < 0) {
            return "Sin limite";
        }
        double available = creditLimit - balance;
        if (available < 0) {
            return "Superado: " + formatCurrency(Math.abs(available));
        }
        return formatCurrency(available);
    }

    private void updatePaymentButtonState() {
        Customer selected = customerCombo.getSelectionModel().getSelectedItem();
        registerPaymentButton.setDisable(selected == null || selected.getId() == null);
        if (registerInitialDebtButton != null) {
            registerInitialDebtButton.setDisable(selected == null || selected.getId() == null);
        }
        if (exportPdfButton != null) {
            exportPdfButton.setDisable(selected == null || selected.getId() == null);
        }
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException();
        }
        String clean = value.trim();
        if (clean.contains(",")) {
            clean = clean.replace(".", "").replace(',', '.');
        }
        return Double.parseDouble(clean);
    }

    private String formatCurrency(double total) {
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

    private String formatDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw).format(UI_DATE_TIME);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String formatBalanceWithState(double balance) {
        if (Math.abs(balance) < 0.000001d) {
            return "Sin deuda";
        }
        if (balance > 0) {
            return "Deuda: " + formatCurrency(balance);
        }
        return "Saldo a favor: " + formatCurrency(Math.abs(balance));
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Aviso");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Informacion");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

