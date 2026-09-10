package com.ferreteria.controllers;

import com.ferreteria.models.Customer;
import com.ferreteria.models.Product;
import com.ferreteria.models.Quote;
import com.ferreteria.models.QuoteLineItem;
import com.ferreteria.repositories.sqlite.SQLiteCustomerRepository;
import com.ferreteria.services.CustomerService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Diálogo para crear un presupuesto: cliente de la BD, items tomados de productos
 * y filas manuales con descripción, cantidad y precio escritos a mano.
 */
public class QuoteFormDialog extends Dialog<Quote> {

    private final CustomerService customerService = new CustomerService(new SQLiteCustomerRepository());

    private final TextField customerSearchField = new TextField();
    private final ComboBox<Customer> customerCombo = new ComboBox<>();
    private final Label customerDetailLabel = new Label();
    private final Spinner<Integer> validDaysSpinner = new Spinner<>(1, 365, 15);
    private final TextArea notesArea = new TextArea();
    private final TableView<QuoteLineItem> itemsTable = new TableView<>();
    private final ObservableList<QuoteLineItem> items = FXCollections.observableArrayList();
    private final Label totalLabel = new Label("$ 0,00");

    private final StringConverter<Double> numberConverter = new StringConverter<>() {
        @Override
        public String toString(Double v) {
            return v == null ? "" : String.format("%.2f", v).replace('.', ',');
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

    private final Quote editing;

    public QuoteFormDialog(Window owner) {
        this(owner, null);
    }

    /** Con {@code existing} en null crea uno nuevo; si no, edita ese presupuesto. */
    public QuoteFormDialog(Window owner, Quote existing) {
        this.editing = existing;
        setTitle(existing == null ? "Nuevo presupuesto" : "Editar presupuesto N° " + existing.getId());
        setHeaderText(existing == null
                ? "Cargue el cliente y los items del presupuesto"
                : "Modifique el cliente o los items del presupuesto");
        initOwner(owner);
        setResizable(true);

        ButtonType saveButton = new ButtonType(
                existing == null ? "Guardar presupuesto" : "Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        setupCustomerCombo();
        setupItemsTable();

        notesArea.setPromptText("Observaciones para el cliente (opcional)");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        HBox customerRow = new HBox(10,
                labeled("Buscar cliente:", customerSearchField),
                labeled("Cliente:", customerCombo),
                labeled("Validez (días):", validDaysSpinner));
        customerRow.setAlignment(Pos.CENTER_LEFT);
        validDaysSpinner.setEditable(true);
        validDaysSpinner.setPrefWidth(90);
        customerSearchField.setPrefWidth(220);
        customerCombo.setPrefWidth(280);

        Button addProductBtn = new Button("Agregar producto");
        addProductBtn.setOnAction(e -> onAddProduct());
        Button addManualBtn = new Button("Agregar fila manual");
        addManualBtn.setOnAction(e -> onAddManual());
        Button addDiscountBtn = new Button("Agregar descuento");
        addDiscountBtn.setOnAction(e -> onAddAdjustment(true));
        Button addSurchargeBtn = new Button("Agregar recargo");
        addSurchargeBtn.setOnAction(e -> onAddAdjustment(false));
        Button removeBtn = new Button("Quitar item");
        removeBtn.setOnAction(e -> onRemoveItem());
        HBox actions = new HBox(10, addProductBtn, addManualBtn, addDiscountBtn, addSurchargeBtn, removeBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        totalLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox totalRow = new HBox(10, new Label("TOTAL:"), totalLabel);
        totalRow.setAlignment(Pos.CENTER_RIGHT);

        VBox top = new VBox(6, customerRow, customerDetailLabel, actions);
        VBox.setMargin(actions, new Insets(6, 0, 0, 0));
        VBox bottom = new VBox(8, new Label("Observaciones"), notesArea, totalRow);

        BorderPane content = new BorderPane();
        content.setTop(top);
        content.setCenter(itemsTable);
        content.setBottom(bottom);
        content.setPadding(new Insets(12));
        BorderPane.setMargin(itemsTable, new Insets(10, 0, 10, 0));
        content.setPrefSize(880, 520);
        getDialogPane().setContent(content);

        loadExisting();

        // El botón guardar queda deshabilitado mientras no haya items.
        Node saveNode = getDialogPane().lookupButton(saveButton);
        saveNode.setDisable(items.isEmpty());
        items.addListener((javafx.collections.ListChangeListener<QuoteLineItem>) c -> {
            saveNode.setDisable(items.isEmpty());
            updateTotal();
        });

        setResultConverter(btn -> btn == saveButton ? buildQuote() : null);
    }

    private HBox labeled(String text, Control control) {
        Label label = new Label(text);
        HBox box = new HBox(6, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.NEVER);
        return box;
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
        customerCombo.setPromptText("Consumidor final");
        customerCombo.getItems().setAll(customerService.getAllActiveCustomers());

        // Con muchos clientes el combo solo no alcanza: se filtra mientras se escribe.
        customerSearchField.setPromptText("Nombre del cliente...");
        customerSearchField.textProperty().addListener((obs, old, query) -> filterCustomers(query));

        customerCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> updateCustomerDetail(selected));
        customerDetailLabel.setStyle("-fx-text-fill: #64748b;");
        updateCustomerDetail(null);
    }

    private void filterCustomers(String query) {
        List<Customer> result = customerService.searchActiveCustomers(query);
        customerCombo.getItems().setAll(result);
        if (result.size() == 1) {
            customerCombo.getSelectionModel().select(result.get(0));
        } else {
            customerCombo.getSelectionModel().clearSelection();
            updateCustomerDetail(null);
            if (!result.isEmpty() && query != null && !query.isBlank()) {
                customerCombo.show();
            }
        }
    }

    private void updateCustomerDetail(Customer customer) {
        if (customer == null) {
            customerDetailLabel.setText("Sin cliente seleccionado: el presupuesto sale a nombre de \"Consumidor final\".");
            return;
        }
        String phone = customer.getPhone() == null || customer.getPhone().isBlank() ? "-" : customer.getPhone();
        String address = customer.getAddress() == null || customer.getAddress().isBlank() ? "-" : customer.getAddress();
        customerDetailLabel.setText(customer.getName() + "  ·  Tel: " + phone + "  ·  " + address);
    }

    private void setupItemsTable() {
        itemsTable.setItems(items);
        itemsTable.setEditable(true);
        itemsTable.setPlaceholder(new Label("Agregue productos de la base o filas manuales."));
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<QuoteLineItem, String> colCode = new TableColumn<>("Código");
        colCode.setCellValueFactory(cd -> cd.getValue().codeProperty());
        colCode.setPrefWidth(110);

        TableColumn<QuoteLineItem, String> colDescription = new TableColumn<>("Descripción");
        colDescription.setCellValueFactory(cd -> cd.getValue().descriptionProperty());
        colDescription.setCellFactory(TextFieldTableCell.forTableColumn());
        colDescription.setOnEditCommit(e -> {
            String value = e.getNewValue();
            if (value != null && !value.isBlank()) {
                e.getRowValue().setDescription(value);
            }
            itemsTable.refresh();
        });
        colDescription.setEditable(true);
        colDescription.setPrefWidth(320);

        TableColumn<QuoteLineItem, Double> colQuantity = new TableColumn<>("Cantidad");
        colQuantity.setCellValueFactory(cd -> cd.getValue().quantityProperty().asObject());
        colQuantity.setCellFactory(col -> buildEditableCell());
        colQuantity.setOnEditCommit(e -> {
            Double v = e.getNewValue();
            if (v != null && v > 0) {
                e.getRowValue().setQuantity(v);
            }
            updateTotal();
            itemsTable.refresh();
        });
        colQuantity.setEditable(true);
        colQuantity.setPrefWidth(100);

        TableColumn<QuoteLineItem, Double> colPrice = new TableColumn<>("P. unitario");
        colPrice.setCellValueFactory(cd -> cd.getValue().priceProperty().asObject());
        colPrice.setCellFactory(col -> buildEditableCell());
        colPrice.setOnEditCommit(e -> {
            Double v = e.getNewValue();
            if (v != null) {
                // Se aceptan negativos: es la forma de cargar un descuento.
                e.getRowValue().setPrice(v);
            }
            updateTotal();
            itemsTable.refresh();
        });
        colPrice.setEditable(true);
        colPrice.setPrefWidth(120);

        TableColumn<QuoteLineItem, Double> colSubtotal = new TableColumn<>("Subtotal");
        colSubtotal.setCellValueFactory(cd -> cd.getValue().subtotalProperty().asObject());
        colSubtotal.setCellFactory(col -> buildEditableCell());
        colSubtotal.setOnEditCommit(e -> {
            Double v = e.getNewValue();
            if (v != null) {
                e.getRowValue().setSubtotal(v);
            }
            updateTotal();
            itemsTable.refresh();
        });
        colSubtotal.setEditable(true);
        colSubtotal.setPrefWidth(130);

        itemsTable.getColumns().addAll(colCode, colDescription, colQuantity, colPrice, colSubtotal);

        // El dialogo es una ventana aparte y no toma styles.css, asi que se centra por codigo.
        // La descripcion queda a la izquierda porque son textos largos.
        for (TableColumn<QuoteLineItem, ?> col : itemsTable.getColumns()) {
            col.setStyle(col == colDescription ? "-fx-alignment: CENTER-LEFT;" : "-fx-alignment: CENTER;");
        }
    }

    private TextFieldTableCell<QuoteLineItem, Double> buildEditableCell() {
        return new TextFieldTableCell<>(numberConverter) {
            @Override
            public void startEdit() {
                super.startEdit();
                if (getGraphic() instanceof TextField tf) {
                    tf.selectAll();
                }
            }
        };
    }

    private void onAddProduct() {
        Product product = ProductSelectionDialog.show(getDialogPane().getScene().getWindow());
        if (product == null) {
            return;
        }
        items.add(new QuoteLineItem(product, 1));
        updateTotal();
    }

    private void onAddManual() {
        QuoteLineItem item = QuoteLineItem.manual("Item manual", 1, 0);
        items.add(item);
        itemsTable.getSelectionModel().select(item);
        updateTotal();
    }

    /**
     * Descuento y recargo son el mismo item manual: cambia el signo del importe,
     * negativo para restar del total y positivo para sumarlo.
     */
    private void onAddAdjustment(boolean discount) {
        String label = discount ? "descuento" : "recargo";
        TextInputDialog input = new TextInputDialog();
        input.initOwner(getDialogPane().getScene().getWindow());
        input.setTitle("Agregar " + label);
        input.setHeaderText("Monto del " + label);
        input.setContentText(discount ? "Importe a descontar:" : "Importe a recargar:");
        Optional<String> answer = input.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        Double amount = numberConverter.fromString(answer.get());
        if (amount == null || amount == 0) {
            warn("Ingrese un importe valido para el " + label + ".");
            return;
        }
        double signed = discount ? -Math.abs(amount) : Math.abs(amount);
        QuoteLineItem item = QuoteLineItem.manual(discount ? "Descuento" : "Recargo", 1, signed);
        items.add(item);
        itemsTable.getSelectionModel().select(item);
        updateTotal();
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void onRemoveItem() {
        QuoteLineItem selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.remove(selected);
            updateTotal();
        }
    }

    private void updateTotal() {
        double total = items.stream().mapToDouble(QuoteLineItem::getSubtotal).sum();
        totalLabel.setText(money(total));
    }

    private void loadExisting() {
        if (editing == null) {
            return;
        }
        if (editing.getCustomerId() != null) {
            customerService.findById(editing.getCustomerId()).ifPresent(c -> {
                if (customerCombo.getItems().stream().noneMatch(x -> x.getId().equals(c.getId()))) {
                    customerCombo.getItems().add(c);
                }
                customerCombo.getSelectionModel().select(c);
            });
        }
        validDaysSpinner.getValueFactory().setValue(editing.getValidDays());
        notesArea.setText(editing.getNotes() == null ? "" : editing.getNotes());
        items.setAll(editing.getItems());
        updateTotal();
    }

    private Quote buildQuote() {
        Customer customer = customerCombo.getSelectionModel().getSelectedItem();
        Quote quote = new Quote();
        quote.setId(editing == null ? null : editing.getId());
        quote.setDate(editing == null ? LocalDateTime.now().toString() : editing.getDate());
        quote.setCustomerId(customer == null ? null : customer.getId());
        quote.setCustomerName(customer == null ? "Consumidor final" : customer.getName());
        quote.setValidDays(validDaysSpinner.getValue() == null ? 15 : validDaysSpinner.getValue());
        quote.setNotes(notesArea.getText());
        quote.getItems().addAll(items);
        return quote;
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

    public static Quote show(Window owner) {
        QuoteFormDialog dialog = new QuoteFormDialog(owner);
        Optional<Quote> result = dialog.showAndWait();
        return result.orElse(null);
    }

    public static Quote showForEdit(Window owner, Quote quote) {
        QuoteFormDialog dialog = new QuoteFormDialog(owner, quote);
        Optional<Quote> result = dialog.showAndWait();
        return result.orElse(null);
    }
}
