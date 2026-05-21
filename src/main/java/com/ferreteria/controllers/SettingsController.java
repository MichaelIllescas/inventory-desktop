package com.ferreteria.controllers;

import com.ferreteria.models.AppSettings;
import com.ferreteria.services.AppSettingsService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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

public class SettingsController {

    @FXML private TextField fieldName;
    @FXML private TextField fieldAddress;
    @FXML private TextField fieldPhone;
    @FXML private TextField fieldCuit;
    @FXML private ImageView logoPreview;
    @FXML private Label logoPathLabel;
    @FXML private Button btnRemoveLogo;

    private final AppSettingsService service = new AppSettingsService();
    private AppSettings current;

    @FXML
    public void initialize() {
        current = service.load();
        fieldName.setText(safe(current.getBusinessName()));
        fieldAddress.setText(safe(current.getBusinessAddress()));
        fieldPhone.setText(safe(current.getBusinessPhone()));
        fieldCuit.setText(safe(current.getBusinessCuit()));
        refreshLogoPreview();
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
