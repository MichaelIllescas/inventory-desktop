package com.ferreteria.controllers;

import com.ferreteria.models.Expense;
import com.ferreteria.repositories.sqlite.SQLiteExpenseRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN;

public class ExpensesController {

    private static final List<String> CATEGORIES = List.of(
            "Servicios", "Alquiler", "Proveedores", "Personal", "Impuestos", "Mantenimiento", "Otros"
    );
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter STORE_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @FXML private TextField fieldDescription;
    @FXML private ComboBox<String> comboCategory;
    @FXML private TextField fieldAmount;
    @FXML private DatePicker datePicker;
    @FXML private DatePicker dateFrom;
    @FXML private DatePicker dateTo;
    @FXML private Label summaryLabel;
    @FXML private TableView<Expense> expensesTable;
    @FXML private TableColumn<Expense, String> colDate;
    @FXML private TableColumn<Expense, String> colDescription;
    @FXML private TableColumn<Expense, String> colCategory;
    @FXML private TableColumn<Expense, Number> colAmount;

    private final SQLiteExpenseRepository repo = new SQLiteExpenseRepository();

    @FXML
    public void initialize() {
        expensesTable.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        comboCategory.getItems().setAll(CATEGORIES);
        comboCategory.getSelectionModel().selectFirst();

        datePicker.setValue(LocalDate.now());

        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.withDayOfMonth(1));
        dateTo.setValue(today);

        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colDate.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(""); return; }
                try {
                    setText(LocalDateTime.parse(item, STORE_FMT).format(DISPLAY_FMT));
                } catch (Exception e) {
                    setText(item);
                }
            }
        });
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colAmount.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });

        loadExpenses();
    }

    @FXML
    private void onSave() {
        String description = fieldDescription.getText().trim();
        String category = comboCategory.getValue();
        String amountText = fieldAmount.getText().trim().replace(",", ".");
        LocalDate date = datePicker.getValue();

        if (description.isEmpty()) { showError("Ingresá una descripción."); return; }
        if (amountText.isEmpty()) { showError("Ingresá un monto."); return; }
        if (date == null) { showError("Seleccioná una fecha."); return; }

        double amount;
        try {
            amount = Double.parseDouble(amountText);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("El monto debe ser un número positivo.");
            return;
        }

        String dateStr = LocalDateTime.of(date, LocalTime.now()).format(STORE_FMT);
        repo.save(new Expense(dateStr, description, category, amount));

        fieldDescription.clear();
        fieldAmount.clear();
        datePicker.setValue(LocalDate.now());
        loadExpenses();
    }

    @FXML
    private void onDelete() {
        Expense selected = expensesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        ButtonType confirm = new ButtonType("Eliminar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar el gasto \"" + selected.getDescription() + "\"?", confirm, cancel);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText("Eliminar gasto");
        if (alert.showAndWait().orElse(cancel) != confirm) return;
        repo.delete(selected.getId());
        loadExpenses();
    }

    @FXML
    private void onFilter() {
        loadExpenses();
    }

    @FXML
    private void onThisMonth() {
        LocalDate today = LocalDate.now();
        dateFrom.setValue(today.withDayOfMonth(1));
        dateTo.setValue(today);
        loadExpenses();
    }

    private void loadExpenses() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from == null || to == null) return;

        String fromStr = LocalDateTime.of(from, LocalTime.MIDNIGHT).format(STORE_FMT);
        String toStr = LocalDateTime.of(to, LocalTime.of(23, 59, 59)).format(STORE_FMT);

        List<Expense> expenses = repo.findInRange(fromStr, toStr);
        expensesTable.getItems().setAll(expenses);

        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        summaryLabel.setText(expenses.size() + " gastos  |  Total " + formatCurrency(total));
    }

    private static String formatCurrency(double total) {
        String num = String.format("%.2f", total).replace('.', ',');
        int i = num.indexOf(',');
        if (i > 3) {
            StringBuilder sb = new StringBuilder(num);
            for (int j = i - 3; j > 0; j -= 3) sb.insert(j, '.');
            num = sb.toString();
        }
        return "$ " + num;
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.WARNING, message).showAndWait();
    }
}
