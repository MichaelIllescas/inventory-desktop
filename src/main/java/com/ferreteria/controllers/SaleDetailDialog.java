package com.ferreteria.controllers;

import com.ferreteria.models.SaleDetailRow;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN;

/**
 * Diálogo de solo lectura con los productos que incluyó una venta.
 * Se usa desde Reportes y desde Cuenta corriente.
 */
public final class SaleDetailDialog {

    private SaleDetailDialog() {
    }

    public static void show(Window owner, int saleId, List<SaleDetailRow> details) {
        if (details == null || details.isEmpty()) {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalle de venta");
        dialog.setHeaderText("Venta N° " + saleId + " - " + formatSaleDate(details.get(0).getSaleDate()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        TableView<SaleDetailRow> detailTable = new TableView<>();
        detailTable.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailTable.setPrefSize(760, 320);

        TableColumn<SaleDetailRow, String> codeCol = new TableColumn<>("Código");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        codeCol.setPrefWidth(90);

        TableColumn<SaleDetailRow, String> productCol = new TableColumn<>("Producto");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productName"));
        productCol.setPrefWidth(260);

        TableColumn<SaleDetailRow, Number> qtyCol = new TableColumn<>("Cant.");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatQuantity(item.doubleValue()));
            }
        });

        TableColumn<SaleDetailRow, Number> priceCol = new TableColumn<>("P. unit. ($)");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        priceCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });

        TableColumn<SaleDetailRow, Number> subtotalCol = new TableColumn<>("Subtotal ($)");
        subtotalCol.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        subtotalCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : formatCurrency(item.doubleValue()));
            }
        });

        detailTable.getColumns().setAll(codeCol, productCol, qtyCol, priceCol, subtotalCol);
        detailTable.getItems().setAll(details);

        SaleDetailRow first = details.get(0);
        Label totalLabel = new Label("Total: " + formatCurrency(first.getSaleTotal())
                + "   |   Medio de pago: " + safePayment(first.getPaymentMethod()));
        totalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        VBox content = new VBox(10, detailTable, totalLabel);
        content.setPadding(new javafx.geometry.Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(820);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    static String formatSaleDate(String value) {
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

    static String safePayment(String paymentMethod) {
        return paymentMethod == null || paymentMethod.isBlank() ? "-" : paymentMethod;
    }

    static String formatCurrency(double total) {
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

    static String formatQuantity(double quantity) {
        if (Math.abs(quantity - Math.rint(quantity)) < 0.000001d) {
            return String.format("%.0f", quantity);
        }
        return String.format("%.2f", quantity).replace('.', ',');
    }
}
