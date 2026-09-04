package com.ferreteria.controllers;

import com.ferreteria.models.AppSettings;
import com.ferreteria.models.TicketData;
import com.ferreteria.services.AppSettingsService;
import com.ferreteria.services.TicketService;
import com.ferreteria.util.TicketPrinter;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class SettingsController {

    @FXML private TextField fieldName;
    @FXML private TextField fieldAddress;
    @FXML private TextField fieldPhone;
    @FXML private TextField fieldCuit;
    @FXML private ImageView logoPreview;
    @FXML private Label logoPathLabel;
    @FXML private Button btnRemoveLogo;
    @FXML private ComboBox<String> printerCombo;
    @FXML private ComboBox<String> paperWidthCombo;

    /** Opción para no fijar impresora y dejar que Windows elija. */
    private static final String DEFAULT_PRINTER_OPTION = "(Predeterminada de Windows)";
    private static final String WIDTH_80 = "80 mm (estándar)";
    private static final String WIDTH_58 = "58 mm";

    private final AppSettingsService service = new AppSettingsService();
    private final TicketService ticketService = new TicketService();
    private AppSettings current;

    @FXML
    public void initialize() {
        current = service.load();
        fieldName.setText(safe(current.getBusinessName()));
        fieldAddress.setText(safe(current.getBusinessAddress()));
        fieldPhone.setText(safe(current.getBusinessPhone()));
        fieldCuit.setText(safe(current.getBusinessCuit()));
        refreshLogoPreview();
        loadPrinters();
        paperWidthCombo.getItems().setAll(WIDTH_80, WIDTH_58);
        paperWidthCombo.getSelectionModel().select(
                current.getTicketPaperWidthMm() == 58 ? WIDTH_58 : WIDTH_80);
    }

    @FXML
    private void onRefreshPrinters() {
        loadPrinters();
    }

    /**
     * Imprime un ticket de ejemplo con la configuración actual, sin guardarla.
     * Sirve para verificar impresora y ancho de rollo antes de vender.
     */
    @FXML
    private void onTestTicket() {
        AppSettings preview = snapshotForPreview();
        TicketData data = new TicketData();
        data.setSettings(preview);
        data.setPaymentMethod("Efectivo");
        data.addLine(new TicketData.Line("0001", "Producto de prueba", 2, 1500, 3000));
        data.addLine(new TicketData.Line("0002", "Otro producto de prueba", 1, 750.5, 750.5));
        data.setTotal(3750.5);
        try {
            ticketService.print(data);
            showInfo("Ticket de prueba enviado a la impresora.");
        } catch (Exception e) {
            showError("No se pudo imprimir el ticket de prueba: " + e.getMessage());
        }
    }

    private void loadPrinters() {
        List<String> printers = TicketPrinter.listPrinters();
        printerCombo.getItems().setAll(DEFAULT_PRINTER_OPTION);
        printerCombo.getItems().addAll(printers);
        String saved = current.getTicketPrinter();
        if (saved != null && !saved.isBlank() && printers.contains(saved)) {
            printerCombo.getSelectionModel().select(saved);
        } else {
            printerCombo.getSelectionModel().select(DEFAULT_PRINTER_OPTION);
        }
    }

    /** Copia de la configuración con lo que hay en pantalla, sin persistir. */
    private AppSettings snapshotForPreview() {
        AppSettings preview = new AppSettings();
        preview.setBusinessName(fieldName.getText().trim());
        preview.setBusinessAddress(fieldAddress.getText().trim());
        preview.setBusinessPhone(fieldPhone.getText().trim());
        preview.setBusinessCuit(fieldCuit.getText().trim());
        preview.setLogoPath(current.getLogoPath());
        preview.setTicketPrinter(selectedPrinter());
        preview.setTicketPaperWidthMm(selectedPaperWidth());
        return preview;
    }

    private String selectedPrinter() {
        String selected = printerCombo.getSelectionModel().getSelectedItem();
        return (selected == null || DEFAULT_PRINTER_OPTION.equals(selected)) ? "" : selected;
    }

    private double selectedPaperWidth() {
        return WIDTH_58.equals(paperWidthCombo.getSelectionModel().getSelectedItem()) ? 58 : 80;
    }

    @FXML
    private void onChooseLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar logo del negocio");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = chooser.showOpenDialog(fieldName.getScene().getWindow());
        if (file == null) return;
        try {
            Path dest = getLogoStoragePath();
            Files.createDirectories(dest.getParent());
            Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            current.setLogoPath(dest.toString());
            refreshLogoPreview();
        } catch (IOException e) {
            showError("No se pudo copiar el logo: " + e.getMessage());
        }
    }

    @FXML
    private void onRemoveLogo() {
        current.setLogoPath(null);
        refreshLogoPreview();
    }

    @FXML
    private void onSave() {
        current.setBusinessName(fieldName.getText().trim());
        current.setBusinessAddress(fieldAddress.getText().trim());
        current.setBusinessPhone(fieldPhone.getText().trim());
        current.setBusinessCuit(fieldCuit.getText().trim());
        current.setTicketPrinter(selectedPrinter());
        current.setTicketPaperWidthMm(selectedPaperWidth());
        try {
            service.save(current);
            showInfo("Configuración guardada correctamente.");
        } catch (Exception e) {
            showError("No se pudo guardar: " + e.getMessage());
        }
    }

    private void refreshLogoPreview() {
        if (current.hasLogo()) {
            try {
                Image img = new Image(new File(current.getLogoPath()).toURI().toString(),
                        200, 80, true, true);
                logoPreview.setImage(img);
                logoPathLabel.setText(Paths.get(current.getLogoPath()).getFileName().toString());
                btnRemoveLogo.setDisable(false);
            } catch (Exception e) {
                clearLogo();
            }
        } else {
            clearLogo();
        }
    }

    private void clearLogo() {
        logoPreview.setImage(null);
        logoPathLabel.setText("Sin logo seleccionado");
        btnRemoveLogo.setDisable(true);
    }

    private Path getLogoStoragePath() {
        String dbFolder = com.ferreteria.database.DatabaseManager.getDataFolder();
        return Paths.get(dbFolder, "business_logo.png");
    }

    private String safe(String v) { return v == null ? "" : v; }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
