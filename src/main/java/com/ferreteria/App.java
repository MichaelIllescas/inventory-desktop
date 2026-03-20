package com.ferreteria;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class App extends Application {

    private static final String LOG_FILE = getLogPath();

    private static String getLogPath() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            return Paths.get(local, "Sistema de Inventario", "error.log").toString();
        }
        return Paths.get(System.getProperty("user.home"), "sistema-inventario-error.log").toString();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-layout.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 700);
        primaryStage.setTitle("Sistema de inventario");
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/app_icon.png")));
        } catch (Exception ignored) { }
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        System.err.println("Sistema de Inventario: iniciando...");
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> showError("Error no esperado", e));
        try {
            launch(args);
        } catch (Throwable e) {
            showError("Error al iniciar la aplicacion", e);
        }
    }

    private static void showError(String title, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();

        System.err.println("=== " + title + " ===");
        System.err.println(msg);
        e.printStackTrace(System.err);
        System.err.println("Log: " + LOG_FILE);

        try {
            Path dir = Paths.get(LOG_FILE).getParent();
            if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String line = LocalDateTime.now().format(fmt) + " " + title + "\n" + stack + "\n\n";
            Files.writeString(Paths.get(LOG_FILE), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }

        try {
            if (Platform.isFxApplicationThread()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Sistema de Inventario - Error");
                alert.setHeaderText(title);
                alert.setContentText(msg + "\n\nDetalle guardado en:\n" + LOG_FILE);
                alert.showAndWait();
            } else {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Sistema de Inventario - Error");
                    alert.setHeaderText(title);
                    alert.setContentText(msg + "\n\nDetalle guardado en:\n" + LOG_FILE);
                    alert.showAndWait();
                });
            }
        } catch (Exception ignored) {
            System.err.println("No se pudo mostrar ventana de error. Revisa " + LOG_FILE);
        }
        esperarEnter();
    }

    private static void esperarEnter() {
        System.out.println("");
        System.out.println("Presione Enter para cerrar...");
        try (Scanner sc = new Scanner(System.in)) {
            sc.nextLine();
        } catch (Exception ignored) { }
    }
}

