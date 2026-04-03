package com.ferreteria.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Logger operacional de la aplicación. Escribe en app.log (flujo funcional).
 * Los errores fatales no capturados siguen yendo a error.log (App.java).
 *
 * Uso:
 *   AppLogger.beginOperation();               // genera operationId para la operación actual
 *   AppLogger.info("SalesController", "registerSale", "inicio");
 *   AppLogger.error("SaleService", "registerSale", "stock insuficiente", ex);
 *   AppLogger.endOperation();                 // limpia el operationId del hilo
 */
public class AppLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ThreadLocal<String> OP_ID = new ThreadLocal<>();
    private static volatile Path logPath;

    private AppLogger() {}

    // ── API pública ──────────────────────────────────────────────────────────

    public static void info(String module, String action, String message) {
        write("INFO ", module, action, message, null);
    }

    public static void warn(String module, String action, String message) {
        write("WARN ", module, action, message, null);
    }

    public static void error(String module, String action, String message) {
        write("ERROR", module, action, message, null);
    }

    public static void error(String module, String action, String message, Throwable t) {
        write("ERROR", module, action, message, t);
    }

    // ── operationId ──────────────────────────────────────────────────────────

    /** Inicia una operación trazable y devuelve el id generado. */
    public static String beginOperation() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        OP_ID.set(id);
        return id;
    }

    /** Propaga un operationId existente al hilo actual (para capas internas). */
    public static void setOperationId(String id) {
        OP_ID.set(id);
    }

    /** Limpia el operationId del hilo actual. Llamar siempre en finally. */
    public static void endOperation() {
        OP_ID.remove();
    }

    public static String currentOperationId() {
        String id = OP_ID.get();
        return id != null ? id : "-";
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static void write(String level, String module, String action, String message, Throwable t) {
        try {
            Path path = resolveLogPath();
            String opId = currentOperationId();
            String timestamp = LocalDateTime.now().format(FMT);
            StringBuilder sb = new StringBuilder();
            sb.append(timestamp)
              .append(" | ").append(level)
              .append(" | ").append(module)
              .append(" | ").append(action)
              .append(" | op=").append(opId)
              .append(" | ").append(message);
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                sb.append("\n").append(sw);
            }
            sb.append("\n");
            Files.writeString(path, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // No interrumpir la app si el log falla
        }
    }

    private static Path resolveLogPath() {
        if (logPath != null) return logPath;
        synchronized (AppLogger.class) {
            if (logPath != null) return logPath;
            Path dir;
            boolean dev = "true".equals(System.getProperty("sistema.inventario.dev"));
            if (dev) {
                dir = Paths.get("data");
            } else {
                String local = System.getenv("LOCALAPPDATA");
                dir = local != null
                        ? Paths.get(local, "Sistema de Inventario")
                        : Paths.get(System.getProperty("user.home"), "Sistema de Inventario");
            }
            try { Files.createDirectories(dir); } catch (IOException ignored) {}
            logPath = dir.resolve("app.log");
        }
        return logPath;
    }
}
