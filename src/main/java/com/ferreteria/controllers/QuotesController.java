package com.ferreteria.controllers;

import com.ferreteria.models.AppSettings;
import com.ferreteria.models.Customer;
import com.ferreteria.models.Quote;
import com.ferreteria.repositories.sqlite.SQLiteCustomerRepository;
import com.ferreteria.repositories.sqlite.SQLiteQuoteRepository;
import com.ferreteria.services.AppSettingsService;
import com.ferreteria.services.CustomerService;
import com.ferreteria.services.QuoteService;
import com.ferreteria.util.AppLogger;
import com.ferreteria.util.QuotePdfExporter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuotesController {

    private static final DateTimeFormatter UI_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final QuoteService quoteService = new QuoteService(new SQLiteQuoteRepository());
    private final AppSettingsService appSettingsService = new AppSettingsService();
    private final CustomerService customerService = new CustomerService(new SQLiteCustomerRepository());
    private final ObservableList<Quote> quotes = FXCollections.observableArrayList();
    /** Listado completo tal como vino de la BD; `quotes` es lo que se ve tras filtrar. */
    private final List<Quote> allQuotes = new ArrayList<>();

    @FXML
    private TableView<Quote> quotesTable;
    @FXML
    private TableColumn<Quote, Integer> colId;
    @FXML
    private TableColumn<Quote, String> colDate;
    @FXML
    private TableColumn<Quote, String> colCustomer;
    @FXML
    private TableColumn<Quote, Integer> colItems;
    @FXML
    private TableColumn<Quote, String> colValidUntil;
    @FXML
    private TableColumn<Quote, Double> colTotal;
    @FXML
    private TableColumn<Quote, Void> colActions;
    @FXML
    private TextField searchField;

    @FXML
    public void initialize() {
        setupTable();
        searchField.textProperty().addListener((obs, old, query) -> applyFilter(query));
        reload();
    }

    private void setupTable() {
        quotesTable.setItems(quotes);
        quotesTable.setPlaceholder(new Label("Todavía no hay presupuestos cargados."));
        quotesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colItems.setCellValueFactory(new PropertyValueFactory<>("itemCount"));

        colDate.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(formatDateTime(cd.getValue().getDate())));
        colValidUntil.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(formatValidUntil(cd.getValue())));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
        colTotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value));
            }
        });

        setupActionsColumn();
    }

    private void setupActionsColumn() {
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button downloadButton = new Button("↓ PDF");
            private final Button editButton = new Button("Editar");
            private final Button deleteButton = new Button("Eliminar");
            private final HBox box = new HBox(6, downloadButton, editButton, deleteButton);

            {
                downloadButton.getStyleClass().add("report-detail-button");
                editButton.getStyleClass().add("report-edit-button");
                deleteButton.getStyleClass().add("report-delete-button");
                // Ancho fijo igual para los tres, y USE_PREF_SIZE para que la politica de
                // resize de la tabla no achique la columna truncando el texto.
                for (Button b : List.of(downloadButton, editButton, deleteButton)) {
                    b.setPrefWidth(80);
                    b.setMinWidth(Region.USE_PREF_SIZE);
                }
                box.setMinWidth(Region.USE_PREF_SIZE);
                box.setAlignment(Pos.CENTER);
                downloadButton.setOnAction(e -> exportPdf(getTableView().getItems().get(getIndex())));
                editButton.setOnAction(e -> editQuote(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(e -> deleteQuote(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void reload() {
        allQuotes.clear();
        allQuotes.addAll(quoteService.getAllQuotes());
        applyFilter(searchField.getText());
    }

    private void applyFilter(String query) {
        if (query == null || query.isBlank()) {
            quotes.setAll(allQuotes);
            return;
        }
        String needle = query.trim().toLowerCase();
        quotes.setAll(allQuotes.stream()
                .filter(q -> matchesCustomer(q, needle) || matchesNumber(q, needle))
                .toList());
    }

    private boolean matchesCustomer(Quote quote, String needle) {
        return quote.getCustomerName() != null
                && quote.getCustomerName().toLowerCase().contains(needle);
    }

    /** El numero se busca exacto: escribir "7" no deberia traer tambien el 17 y el 27. */
    private boolean matchesNumber(Quote quote, String needle) {
        return quote.getId() != null && String.valueOf(quote.getId()).equals(needle);
    }

    @FXML
    private void onNewQuote() {
        Quote draft = QuoteFormDialog.show(quotesTable.getScene().getWindow());
        if (draft == null) {
            return;
        }
        try {
            Quote saved = quoteService.createQuote(draft);
            reload();
            quotesTable.getSelectionModel().select(0);
            showInfo("Presupuesto N° " + saved.getId() + " creado correctamente.");
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        } catch (Exception e) {
            AppLogger.error("QuotesController", "onNewQuote", "Error al crear presupuesto: " + e.getMessage(), e);
            showError("No se pudo crear el presupuesto: " + e.getMessage());
        }
    }

    private void exportPdf(Quote selected) {
        if (selected == null || selected.getId() == null) {
            return;
        }
        Optional<Quote> full = quoteService.findById(selected.getId());
        if (full.isEmpty()) {
            showWarning("No se encontró el presupuesto seleccionado.");
            return;
        }
        Quote quote = full.get();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar presupuesto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        String safeName = quote.getCustomerName() == null
                ? "cliente"
                : quote.getCustomerName().replaceAll("[^a-zA-Z0-9-_ ]", "_");
        chooser.setInitialFileName("presupuesto-" + quote.getId() + "-" + safeName + ".pdf");
        File file = chooser.showSaveDialog(quotesTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            AppSettings settings = appSettingsService.load();
            Customer customer = quote.getCustomerId() == null
                    ? null
                    : customerService.findById(quote.getCustomerId()).orElse(null);
            QuotePdfExporter.export(Path.of(file.getAbsolutePath()), quote, customer, settings);
            showInfo("PDF exportado correctamente.");
        } catch (Exception e) {
            AppLogger.error("QuotesController", "onExportPdf", "Error al exportar PDF: " + e.getMessage(), e);
            showError("No se pudo exportar PDF: " + e.getMessage());
        }
    }

    private void editQuote(Quote selected) {
        if (selected == null || selected.getId() == null) {
            return;
        }
        Optional<Quote> full = quoteService.findById(selected.getId());
        if (full.isEmpty()) {
            showWarning("No se encontró el presupuesto seleccionado.");
            return;
        }
        Quote edited = QuoteFormDialog.showForEdit(quotesTable.getScene().getWindow(), full.get());
        if (edited == null) {
            return;
        }
        try {
            quoteService.updateQuote(edited);
            reload();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        } catch (Exception e) {
            AppLogger.error("QuotesController", "editQuote", "Error al editar presupuesto: " + e.getMessage(), e);
            showError("No se pudo editar el presupuesto: " + e.getMessage());
        }
    }

    private void deleteQuote(Quote selected) {
        if (selected == null || selected.getId() == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar presupuesto");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Eliminar el presupuesto N° " + selected.getId() + " de "
                + selected.getCustomerName() + "?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            quoteService.deleteQuote(selected.getId());
            reload();
        } catch (Exception e) {
            AppLogger.error("QuotesController", "deleteQuote", "Error al eliminar presupuesto: " + e.getMessage(), e);
            showError("No se pudo eliminar el presupuesto: " + e.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        reload();
    }

    private String formatDateTime(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.parse(value).format(UI_DATE_TIME);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String formatValidUntil(Quote quote) {
        if (quote.getDate() == null || quote.getDate().isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.parse(quote.getDate())
                    .toLocalDate()
                    .plusDays(Math.max(quote.getValidDays(), 0))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
            return "-";
        }
    }

    private static String money(double total) {
        String num = String.format("%.2f", total).replace('.', ',');
        int i = num.indexOf(',');
        if (i > 3) {
            StringBuilder sb = new StringBuilder(num);
            for (int j = i - 3; j > 0; j -= 3) sb.insert(j, '.');
            num = sb.toString();
        }
        return "$ " + num;
    }

    private void showInfo(String message) {
        showAlert(Alert.AlertType.INFORMATION, message);
    }

    private void showWarning(String message) {
        showAlert(Alert.AlertType.WARNING, message);
    }

    private void showError(String message) {
        showAlert(Alert.AlertType.ERROR, message);
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
