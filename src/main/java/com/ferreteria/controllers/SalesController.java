package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.models.SaleLineItem;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;
import com.ferreteria.services.SaleService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.converter.DoubleStringConverter;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

public class SalesController {

    @FXML
    private TextField scanField;

    @FXML
    private javafx.scene.control.ComboBox<String> paymentMethodCombo;

    @FXML
    private TableView<SaleLineItem> itemsTable;

    @FXML
    private TableColumn<SaleLineItem, String> colCode;

    @FXML
    private TableColumn<SaleLineItem, String> colName;

    @FXML
    private TableColumn<SaleLineItem, Double> colQuantity;

    @FXML
    private TableColumn<SaleLineItem, Number> colUnitPrice;

    @FXML
    private TableColumn<SaleLineItem, Number> colSubtotal;

    @FXML
    private javafx.scene.control.Label totalLabel;

    private final SaleService saleService;
    private final ObservableList<SaleLineItem> items = FXCollections.observableArrayList();

    public SalesController() {
        this.saleService = new SaleService(
                new SQLiteSaleRepository(),
                new SQLiteProductRepository()
        );
    }

    @FXML
    public void initialize() {
        setupTable();
        itemsTable.setItems(items);
        updateTotal();
        items.addListener((ListChangeListener<? super SaleLineItem>) c -> updateTotal());
        // Cuando cambie la cantidad en una línea, actualizar total
        scanField.setPromptText("Escanee o escriba código y pulse Enter");
        // Al abrir el panel de ventas, enfocar directamente el campo de código
        Platform.runLater(() -> {
            scanField.requestFocus();
            // Atajo global: Enter confirma venta si el campo de código está vacío
            if (scanField.getScene() != null) {
                scanField.getScene().addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        // Si el foco está en scanField, dejamos que maneje su propio Enter
                        if (scanField.isFocused()) return;
                        if (!items.isEmpty()) {
                            event.consume();
                            confirmAndRegisterSale();
                        }
                    }
                });
            }
        });

        // Medios de pago disponibles (por ahora fijo, luego se puede llevar a BD)
        if (paymentMethodCombo != null) {
            paymentMethodCombo.setItems(FXCollections.observableArrayList(
                    "Efectivo",
                    "Transferencia",
                    "Débito",
                    "Crédito"
            ));
            paymentMethodCombo.getSelectionModel().selectFirst();
        }
    }

    private void setupTable() {
        colCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        // Precios y subtotales como número simple; el signo $ queda en el encabezado de columna

        // Celda de cantidad (decimal, ej. kg): commit al salir del campo o con Enter
        DoubleStringConverter converter = new DoubleStringConverter();
        colQuantity.setCellFactory(col -> {
            TextFieldTableCell<SaleLineItem, Double> cell = new TextFieldTableCell<>(converter) {
                @Override
                public void startEdit() {
                    super.startEdit();
                    if (getGraphic() instanceof TextField) {
                        TextField tf = (TextField) getGraphic();
                        tf.focusedProperty().addListener((obs, wasFocused, nowFocused) -> {
                            if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(nowFocused)) {
                                try {
                                    String t = tf.getText();
                                    double v = t != null && !t.isBlank() ? Double.parseDouble(t.trim().replace(',', '.')) : 1.0;
                                    if (v > 0) commitEdit(v);
                                    else commitEdit(1.0);
                                } catch (NumberFormatException e) {
                                    commitEdit(getItem() != null ? getItem() : 1.0);
                                }
                            }
                        });
                    }
                }
            };
            return cell;
        });
        colQuantity.setOnEditCommit(e -> {
            SaleLineItem line = e.getRowValue();
            double v = e.getNewValue() != null && e.getNewValue() > 0 ? e.getNewValue() : 1.0;
            line.setQuantity(v);
            updateTotal();
        });
        itemsTable.setEditable(true);
        colQuantity.setEditable(true);
    }

    private void updateTotal() {
        double total = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        totalLabel.setText(String.format("Total: %.2f", total));
    }

    @FXML
    private void onScanFieldKeyPressed(javafx.scene.input.KeyEvent event) {
        if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
            event.consume();
            String code = scanField.getText();
            if (code != null && !code.isBlank()) {
                addProductByCode(code);
                scanField.clear();
                scanField.requestFocus();
            } else {
                // Si no hay código y hay ítems, usar Enter como atajo para confirmar la venta
                confirmAndRegisterSale();
            }
        }
    }

    private void addProductByCode(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        Optional<Product> opt = saleService.findProductByCode(code.trim());
        if (opt.isEmpty()) {
            showWarning("No se encontró ningún producto con código: " + code.trim());
            return;
        }
        Product product = opt.get();
        SaleLineItem existing = items.stream()
                .filter(l -> l.getProductId() == product.getId())
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + 1.0);
        } else {
            items.add(new SaleLineItem(product, 1.0));
        }
        Platform.runLater(() -> updateTotal());
    }

    @FXML
    private void onAddProduct() {
        Product product = ProductSelectionDialog.show(itemsTable.getScene().getWindow());
        if (product != null) {
            SaleLineItem existing = items.stream()
                    .filter(l -> l.getProductId() == product.getId())
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + 1.0);
            } else {
                items.add(new SaleLineItem(product, 1.0));
            }
        }
    }

    @FXML
    private void onRemoveLine() {
        SaleLineItem selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.remove(selected);
        } else {
            showWarning("Seleccione una línea para quitar.");
        }
    }

    @FXML
    private void onRegisterSale() {
        confirmAndRegisterSale();
    }

    private void confirmAndRegisterSale() {
        if (items.isEmpty()) {
            showWarning("No hay ítems en la venta.");
            return;
        }
        String payment = paymentMethodCombo != null ? paymentMethodCombo.getSelectionModel().getSelectedItem() : null;
        if (payment == null || payment.isBlank()) {
            showWarning("Seleccione un medio de pago.");
            return;
        }
        double total = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar venta");
        confirm.setHeaderText("¿Registrar la venta?");
        confirm.setContentText(String.format("Total: %.2f%n%n¿Desea confirmar la venta?", total));
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        // Ejecutar el registro real de la venta
        try {
            saleService.registerSale(items, payment);
            items.clear();
            showInfo("Venta registrada correctamente.");
            scanField.requestFocus();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onClear() {
        items.clear();
        scanField.clear();
        scanField.requestFocus();
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
        alert.setTitle("Información");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
