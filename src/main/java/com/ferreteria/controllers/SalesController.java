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
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.Cursor;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

import com.ferreteria.util.AppLogger;

import java.util.Optional;

public class SalesController {

    @FXML private TextField scanField;
    @FXML private javafx.scene.control.ComboBox<String> paymentMethodCombo;
    @FXML private TableView<SaleLineItem> itemsTable;
    @FXML private TableColumn<SaleLineItem, String> colCode;
    @FXML private TableColumn<SaleLineItem, String> colName;
    @FXML private TableColumn<SaleLineItem, Double> colQuantity;
    @FXML private TableColumn<SaleLineItem, Double> colUnitPrice;
    @FXML private TableColumn<SaleLineItem, Double> colSubtotal;
    @FXML private Label totalLabel;
    @FXML private Label totalTextLabel;
    @FXML private Label pesoLabel;
    @FXML private TextField totalField;
    @FXML private HBox totalRow;
    @FXML private HBox totalAmountBox;
    @FXML private HBox ajusteBox;
    @FXML private Label ajusteLabel;
    @FXML private VBox summaryItemsBox;
    @FXML private Label summaryCountLabel;
    @FXML private javafx.scene.image.ImageView variosBarcode;
    @FXML private VBox barcodeBox;
    @FXML private HBox bottomMainRow;

    private final SaleService saleService;
    private final ObservableList<SaleLineItem> items = FXCollections.observableArrayList();
    private boolean syncingTotal = false;

    public SalesController() {
        this.saleService = new SaleService(
                new SQLiteSaleRepository(),
                new SQLiteProductRepository()
        );
    }

    @FXML
    public void initialize() {
        AppLogger.info("SalesController", "initialize", "Inicializando panel de ventas");
        setupTable();
        itemsTable.setItems(items);
        updateTotal();
        updateSummary();
        items.addListener((ListChangeListener<? super SaleLineItem>) c -> {
            updateTotal();
            updateSummary();
        });

        scanField.setPromptText("Escanee o escriba código y pulse Enter");

        // Click en la tabla devuelve el foco al campo de escaneo (si no estÃƒÂ¡ editando una celda)
        itemsTable.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
            if (itemsTable.getEditingCell() == null) {
                Platform.runLater(() -> scanField.requestFocus());
            }
        });

        Platform.runLater(() -> {
            scanField.requestFocus();
            if (scanField.getScene() != null) {
                // FILTER (no handler) para capturar Enter antes que la tabla lo consuma.
                // Verificamos que scanField siga en la escena: si el usuario navegó a otra
                // vista, getScene() devuelve null y el filtro se ignora sin efectos.
                scanField.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() != KeyCode.ENTER) return;
                    if (scanField.getScene() == null) return; // vista de ventas ya no está activa
                    if (scanField.isFocused()) return;
                    if (totalField.isFocused()) return;
                    if (itemsTable.getEditingCell() != null) return; // hay una celda en edición
                    if (!items.isEmpty()) {
                        event.consume();
                        confirmAndRegisterSale();
                    }
                });
            }
        });

        if (paymentMethodCombo != null) {
            paymentMethodCombo.setItems(FXCollections.observableArrayList(
                    "Efectivo", "Transferencia", "D\u00E9bito", "Cr\u00E9dito"
            ));
            paymentMethodCombo.getSelectionModel().selectFirst();
        }

        pesoLabel.setText("$");

        // Generar barcode de Varios
        if (variosBarcode != null) {
            variosBarcode.setImage(
                com.ferreteria.util.Code128Generator.generateImage(
                    com.ferreteria.database.DatabaseManager.VARIOS_CODE, 1.5, 50));
        }

        // Acepta dÃƒÂ­gitos, punto (separador de miles al mostrar) y coma decimal
        totalField.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            // Solo nÃƒÂºmeros, punto de miles y coma decimal
            if (newText.matches("[0-9.]*[,]?[0-9]*")) return change;
            return null;
        }));

        totalField.setCursor(Cursor.TEXT);
        totalField.setOnAction(e -> {
            if (scanField != null) {
                scanField.requestFocus();
            } else {
                totalField.getParent().requestFocus();
            }
        });

        totalField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                // Al entrar: quita puntos de miles para ediciÃƒÂ³n limpia
                syncingTotal = true;
                String raw = totalField.getText().replace(".", "");
                totalField.setText(raw);
                syncingTotal = false;
                Platform.runLater(() -> { totalField.end(); totalField.deselect(); });
            } else {
                // Al salir: restaura si vacÃƒÂ­o, luego formatea con miles
                String text = totalField.getText();
                if (text == null || text.isBlank()) {
                    double calculated = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
                    syncingTotal = true;
                    totalField.setText(formatTotal(calculated));
                    syncingTotal = false;
                } else {
                    try {
                        double v = Double.parseDouble(text.replace(',', '.'));
                        syncingTotal = true;
                        totalField.setText(formatTotal(v));
                        syncingTotal = false;
                        Platform.runLater(() -> {
                            totalField.end();
                            totalField.deselect();
                            updateTotalFieldWidth();
                        });
                    } catch (NumberFormatException ignored) {}
                }
                updateAjuste();
                updateTotalFieldWidth();
            }
        });

        totalField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!syncingTotal) {
                updateAjuste();
            }
            updateTotalFieldWidth();
            updateSummary();
        });

        if (totalRow != null) {
            totalRow.widthProperty().addListener((obs, oldVal, newVal) -> updateTotalFieldWidth());
        }
        if (bottomMainRow != null) {
            bottomMainRow.widthProperty().addListener((obs, oldVal, newVal) -> {
                updateTotalFieldWidth();
                updateBarcodeWidth();
            });
        }
        if (barcodeBox != null) {
            barcodeBox.widthProperty().addListener((obs, oldVal, newVal) -> updateBarcodeWidth());
        }
        if (variosBarcode != null) {
            variosBarcode.fitHeightProperty().addListener((obs, oldVal, newVal) -> updateBarcodeWidth());
        }

        Platform.runLater(this::updateTotalFieldWidth);
        Platform.runLater(this::updateBarcodeWidth);
    }

    private void setupTable() {
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colCode.setCellValueFactory(cd -> cd.getValue().productCodeProperty());
        colName.setCellValueFactory(cd -> cd.getValue().productNameProperty());
        colQuantity.setCellValueFactory(cd -> cd.getValue().quantityProperty().asObject());
        colUnitPrice.setCellValueFactory(cd -> cd.getValue().unitPriceProperty().asObject());
        colSubtotal.setCellValueFactory(cd -> cd.getValue().subtotalProperty().asObject());

        StringConverter<Double> converter = new StringConverter<>() {
            @Override
            public String toString(Double v) {
                if (v == null) return "";
                return String.format("%.2f", v).replace('.', ',');
            }
            @Override
            public Double fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try {
                    return Double.parseDouble(s.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };

        // Cantidad — editable
        colQuantity.setCellFactory(col -> buildEditableCell(converter));
        colQuantity.setOnEditCommit(e -> {
            SaleLineItem line = e.getRowValue();
            Double v = e.getNewValue();
            if (v != null && v > 0) {
                line.setQuantity(v);
                updateTotal();
                updateSummary();
            }
        });

        // Precio unitario — editable
        colUnitPrice.setCellFactory(col -> buildEditableCell(converter));
        colUnitPrice.setOnEditCommit(e -> {
            SaleLineItem line = e.getRowValue();
            Double v = e.getNewValue();
            if (v != null && v >= 0) {
                line.setUnitPrice(v);
                updateTotal();
                updateSummary();
            }
        });

        // Subtotal — editable como override libre (descuento, ajuste), no toca el precio unitario
        colSubtotal.setCellFactory(col -> buildEditableCell(converter));
        colSubtotal.setOnEditCommit(e -> {
            SaleLineItem line = e.getRowValue();
            Double newSubtotal = e.getNewValue();
            if (newSubtotal != null && newSubtotal >= 0) {
                line.setSubtotal(newSubtotal);
                updateTotal();
                updateSummary();
            }
        });

        itemsTable.setEditable(true);
        colQuantity.setEditable(true);
        colUnitPrice.setEditable(true);
        colSubtotal.setEditable(true);
    }

    private TextFieldTableCell<SaleLineItem, Double> buildEditableCell(StringConverter<Double> converter) {
        return new TextFieldTableCell<>(converter) {
            @Override
            public void startEdit() {
                super.startEdit();
                if (getGraphic() instanceof TextField tf) {
                    tf.selectAll();
                    tf.focusedProperty().addListener((obs, was, now) -> {
                        if (Boolean.TRUE.equals(was) && Boolean.FALSE.equals(now)) {
                            try {
                                Double v = converter.fromString(tf.getText());
                                if (v != null) commitEdit(v);
                                else cancelEdit();
                            } catch (Exception ex) {
                                cancelEdit();
                            }
                        }
                    });
                }
            }
        };
    }

    private void updateTotal() {
        double total = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        totalLabel.setText("$ " + formatTotal(total));
        if (!totalField.isFocused()) {
            syncingTotal = true;
            totalField.setText(formatTotal(total));
            syncingTotal = false;
            Platform.runLater(() -> {
                totalField.end();
                totalField.deselect();
                updateTotalFieldWidth();
            });
        }
        updateAjuste();
    }

    private double parseTotalField(double fallback) {
        String text = totalField.getText();
        if (text == null || text.isBlank()) return fallback;
        try {
            // Soporta "1.500,00" (punto=miles, coma=decimal) y "1500.00" (punto=decimal sin miles)
            String clean = text.trim();
            if (clean.contains(",")) {
                // formato local: quita puntos de miles, cambia coma por punto
                clean = clean.replace(".", "").replace(',', '.');
            }
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String formatTotal(double value) {
        long intPart = (long) value;
        long decPart = Math.round((value - intPart) * 100);
        String intStr = String.format("%,d", intPart).replace(',', '.');
        return String.format("%s,%02d", intStr, decPart);
    }

    private void updateTotalFieldWidth() {
        if (totalField == null || totalRow == null) return;

        String text = totalField.getText();
        String visibleText = (text == null || text.isBlank()) ? "0,00" : text;
        double availableAmountWidth = resolveAvailableAmountWidth();
        double fontSize = resolveAmountFontSize(visibleText, availableAmountWidth);

        applyAmountFontSize(fontSize);

        Text amountMeasure = new Text(visibleText);
        amountMeasure.setFont(Font.font(totalField.getFont().getFamily(), FontWeight.BOLD, fontSize));
        double desiredWidth = Math.ceil(amountMeasure.getLayoutBounds().getWidth()) + 44;
        double finalWidth = Math.max(120, Math.min(desiredWidth, availableAmountWidth));

        totalField.setPrefWidth(finalWidth);
        totalField.setMinWidth(0);
        totalField.setMaxWidth(finalWidth);
    }

    private void updateAjuste() {
        double calculated = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        String text = totalField.getText();
        if (text == null || text.isBlank()) {
            ajusteBox.setVisible(false);
            ajusteBox.setManaged(false);
            return;
        }
        try {
            double custom = parseTotalField(calculated);
            double diff = custom - calculated;
            if (Math.abs(diff) > 0.005) {
                ajusteLabel.setText((diff > 0 ? "Aumento: " : "Descuento: ") + formatSignedAmount(diff));
                ajusteLabel.setStyle(diff > 0
                        ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;"
                        : "-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                ajusteBox.setVisible(true);
                ajusteBox.setManaged(true);
            } else {
                ajusteBox.setVisible(false);
                ajusteBox.setManaged(false);
            }
        } catch (NumberFormatException e) {
            ajusteBox.setVisible(false);
            ajusteBox.setManaged(false);
        }
    }

    private void updateBarcodeWidth() {
        if (variosBarcode == null || barcodeBox == null) return;

        double boxWidth = barcodeBox.getWidth();
        if (bottomMainRow != null && totalRow != null && bottomMainRow.getWidth() > 0) {
            double remainingWidth = bottomMainRow.getWidth() - totalRow.getWidth() - 14;
            if (remainingWidth > 0) {
                boxWidth = Math.min(boxWidth, remainingWidth);
            }
        }
        if (boxWidth <= 0) return;

        double availableWidth = Math.max(180, boxWidth - 8);
        variosBarcode.setFitWidth(availableWidth);
    }

    private double resolveAvailableAmountWidth() {
        double rowWidth = totalRow.getWidth();
        if (bottomMainRow != null && bottomMainRow.getWidth() > 0) {
            double reservedBarcodeWidth = 180;
            if (barcodeBox != null) {
                reservedBarcodeWidth = Math.max(180, barcodeBox.getWidth());
            }
            double labelWidth = 0;
            if (totalTextLabel != null) {
                labelWidth = totalTextLabel.getLayoutBounds().getWidth();
            }
            double available = bottomMainRow.getWidth() - reservedBarcodeWidth - labelWidth - 40;
            return Math.max(120, available);
        }

        if (rowWidth <= 0) {
            return 300;
        }

        double labelWidth = 0;
        if (totalTextLabel != null) {
            labelWidth = totalTextLabel.getLayoutBounds().getWidth();
        }

        double available = rowWidth - labelWidth - 24;
        return Math.max(120, available);
    }

    private double resolveAmountFontSize(String visibleText, double availableAmountWidth) {
        double maxFontSize = 72;
        double minFontSize = 18;

        for (double fontSize = maxFontSize; fontSize >= minFontSize; fontSize -= 2) {
            Text pesoMeasure = new Text(pesoLabel != null ? pesoLabel.getText() : "$");
            pesoMeasure.setFont(Font.font(pesoLabel.getFont().getFamily(), FontWeight.BOLD, fontSize));

            Text amountMeasure = new Text(visibleText);
            amountMeasure.setFont(Font.font(totalField.getFont().getFamily(), FontWeight.BOLD, fontSize));

            double combinedWidth = pesoMeasure.getLayoutBounds().getWidth()
                    + amountMeasure.getLayoutBounds().getWidth()
                    + 56;

            if (combinedWidth <= availableAmountWidth) {
                return fontSize;
            }
        }

        return minFontSize;
    }

    private void applyAmountFontSize(double fontSize) {
        totalField.setStyle("-fx-font-size: " + (int) fontSize + "px; -fx-font-weight: bold;");
        if (pesoLabel != null) {
            pesoLabel.setStyle("-fx-font-size: " + (int) fontSize + "px; -fx-font-weight: bold;");
        }
    }

    private void updateSummary() {
        summaryItemsBox.getChildren().clear();
        if (items.isEmpty()) {
            summaryCountLabel.setText("");
            addSummaryRow(summaryItemsBox, "Sin productos", "—", "#9ca3af");
            return;
        }

        int distintos = items.size();
        double totalUnidades = items.stream().mapToDouble(SaleLineItem::getQuantity).sum();
        double totalCalc = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();

        String unidadesStr = (totalUnidades == Math.floor(totalUnidades))
                ? String.format("%.0f", totalUnidades)
                : String.format("%.2f", totalUnidades).replace('.', ',');
        double totalFinal = parseTotalField(totalCalc);
        double diff = totalFinal - totalCalc;

        addSummaryRow(summaryItemsBox, "Productos distintos", String.valueOf(distintos), "#111827");
        addSummaryRow(summaryItemsBox, "Unidades totales", unidadesStr, "#111827");
        addSummaryRow(summaryItemsBox, "Total calculado", formatCurrency(totalCalc), "#2563eb");
        if (Math.abs(diff) > 0.005) {
            if (diff > 0) {
                addSummaryRow(summaryItemsBox, "Aumento", formatCurrency(diff), "#16a34a");
            } else {
                addSummaryRow(summaryItemsBox, "Descuento", formatCurrency(Math.abs(diff)), "#dc2626");
            }
            addSummaryRow(summaryItemsBox, "Total final", formatCurrency(totalFinal), "#111827");
        }
        summaryCountLabel.setText("");
    }

    private void addSummaryRow(VBox container, String label, String value, String valueColor) {
        VBox row = new VBox(2);
        row.setStyle("-fx-background-color: #f9fafb; -fx-background-radius: 6; -fx-padding: 8 10; -fx-border-color: #e5e7eb; -fx-border-radius: 6; -fx-border-width: 1;");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + valueColor + ";");
        val.setWrapText(true);

        row.getChildren().addAll(lbl, val);
        container.getChildren().add(row);
    }

    private String formatCurrency(double value) {
        return "$ " + formatTotal(value);
    }

    private String formatSignedAmount(double value) {
        String sign = value >= 0 ? "+" : "-";
        return sign + formatTotal(Math.abs(value));
    }

    @FXML
    private void onScanFieldKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            String code = scanField.getText();
            if (code != null && !code.isBlank()) {
                addProductByCode(code);
                scanField.clear();
                scanField.requestFocus();
            } else {
                confirmAndRegisterSale();
            }
        }
    }

    private void addProductByCode(String code) {
        if (code == null || code.isBlank()) return;
        String normalizedCode = code.trim();
        if ("0".equals(normalizedCode)) {
            normalizedCode = com.ferreteria.database.DatabaseManager.VARIOS_CODE;
        }
        AppLogger.info("SalesController", "addProductByCode", "Buscando código: " + normalizedCode);
        Optional<Product> opt = saleService.findProductByCode(normalizedCode);
        if (opt.isEmpty()) {
            AppLogger.warn("SalesController", "addProductByCode", "Código no encontrado: " + normalizedCode);
            showWarning("No se encontró ningún producto con código: " + normalizedCode);
            return;
        }
        AppLogger.info("SalesController", "addProductByCode", "Producto encontrado: id=" + opt.get().getId() + " nombre=" + opt.get().getName());
        addProductToCart(opt.get());
    }

    private void addProductToCart(Product product) {
        boolean isVarios = com.ferreteria.database.DatabaseManager.VARIOS_CODE.equals(product.getCode());
        SaleLineItem existing = isVarios ? null : items.stream()
                .filter(l -> l.getProductId() == product.getId())
                .findFirst().orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + 1.0);
            Platform.runLater(() -> { updateTotal(); updateSummary(); });
        } else {
            SaleLineItem line = new SaleLineItem(product, 1.0);
            items.add(line);
            Platform.runLater(() -> {
                updateTotal();
                updateSummary();
                if (isVarios) {
                    // Abrir ediciÃƒÂ³n del precio directamente para que el usuario lo ingrese
                    int row = items.size() - 1;
                    itemsTable.getSelectionModel().select(row);
                    itemsTable.scrollTo(row);
                    itemsTable.edit(row, colUnitPrice);
                }
            });
        }
    }

    @FXML
    private void onAddProduct() {
        Product product = ProductSelectionDialog.show(itemsTable.getScene().getWindow());
        if (product != null) {
            addProductToCart(product);
        }
    }

    @FXML
    private void onRemoveLine() {
        SaleLineItem selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.remove(selected);
        } else {
            showWarning("Seleccione una l\u00EDnea para quitar.");
        }
    }

    @FXML
    private void onRegisterSale() {
        confirmAndRegisterSale();
    }

    private void confirmAndRegisterSale() {
        if (items.isEmpty()) {
            showWarning("No hay \u00EDtems en la venta.");
            return;
        }
        String payment = paymentMethodCombo != null ? paymentMethodCombo.getSelectionModel().getSelectedItem() : null;
        if (payment == null || payment.isBlank()) {
            showWarning("Seleccione un medio de pago.");
            return;
        }

        double calculated = items.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        double customTotal = parseTotalField(calculated);

        String totalInfo;
        if (Math.abs(customTotal - calculated) > 0.005) {
            totalInfo = String.format(
                    "Calculado: $ %.2f%nA cobrar:   $ %.2f%nAjuste:     %+.2f",
                    calculated, customTotal, customTotal - calculated
            ).replace('.', ',');
        } else {
            totalInfo = String.format("Total: $ %.2f", customTotal).replace('.', ',');
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar venta");
        confirm.setHeaderText("\u00BFRegistrar la venta?");
        confirm.setContentText(totalInfo + "\n\n\u00BFDesea confirmar?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            AppLogger.info("SalesController", "confirmAndRegisterSale", "Venta cancelada por el usuario");
            return;
        }

        String opId = AppLogger.beginOperation();
        try {
            AppLogger.info("SalesController", "confirmAndRegisterSale",
                    "Iniciando registro: items=" + items.size() + " total=" + customTotal + " pago=" + payment);
            saleService.registerSale(items, payment, customTotal, opId);
            AppLogger.info("SalesController", "confirmAndRegisterSale", "Venta registrada correctamente");
            items.clear();
            syncingTotal = true;
            totalField.setText(formatTotal(0));
            syncingTotal = false;
            Platform.runLater(() -> { totalField.end(); totalField.deselect(); });
            updateSummary();
            showInfo("Venta registrada correctamente.");
            scanField.requestFocus();
        } catch (IllegalArgumentException e) {
            AppLogger.warn("SalesController", "confirmAndRegisterSale", "ValidaciÃƒÂ³n fallida: " + e.getMessage());
            showError(e.getMessage());
        } catch (Exception e) {
            AppLogger.error("SalesController", "confirmAndRegisterSale", "Error inesperado al registrar venta", e);
            showError("Error inesperado al registrar la venta. Revis\u00E1 el log para m\u00E1s detalles.");
        } finally {
            AppLogger.endOperation();
        }
    }

    @FXML
    private void onClear() {
        items.clear();
        scanField.clear();
        syncingTotal = true;
        totalField.setText(formatTotal(0));
        syncingTotal = false;
        updateSummary();
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
        alert.setTitle("Informaci\u00F3n");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}

