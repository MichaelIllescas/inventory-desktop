package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.CustomerAccountMovementRow;
import com.ferreteria.models.CustomerDebtRow;
import com.ferreteria.util.AppLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CurrentAccountService {

    private static final String PAYMENT_CURRENT_ACCOUNT = "Cuenta corriente";
    private static final String PAYMENT_CURRENT_ACCOUNT_LEGACY = "CUENTA_CORRIENTE";

    public int registerPayment(int customerId, double amount, String paymentMethod, String notes) {
        if (customerId <= 0) {
            throw new IllegalArgumentException("Cliente inválido.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor a 0.");
        }

        final int[] paymentIdHolder = new int[1];
        final String date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        DatabaseManager.runInTransaction(() -> {
            int paymentId = insertPayment(customerId, date, amount, paymentMethod, notes);
            paymentIdHolder[0] = paymentId;
            insertCreditMovement(customerId, date, amount, paymentId, notes);
            applyPaymentFifo(customerId, paymentId, amount);
        });

        return paymentIdHolder[0];
    }

    public void registerInitialDebt(int customerId, double amount, String notes) {
        if (customerId <= 0) {
            throw new IllegalArgumentException("Cliente invalido.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("El monto de la deuda debe ser mayor a 0.");
        }
        final String date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String sql = "INSERT INTO customer_account_movements(customer_id, date, type, amount, sale_id, payment_id, notes) " +
                "VALUES (?, ?, 'DEBITO', ?, NULL, NULL, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setString(2, date);
            stmt.setDouble(3, amount);
            String movementNote = (notes == null || notes.isBlank()) ? "Deuda anterior" : notes.trim();
            stmt.setString(4, movementNote);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al registrar deuda anterior", e);
        }
    }

    public double getCustomerBalance(int customerId) {
        String sql = "SELECT COALESCE(SUM(CASE " +
                "WHEN type = 'DEBITO' THEN amount " +
                "WHEN type = 'CREDITO' THEN -amount " +
                "ELSE 0 END), 0) AS balance " +
                "FROM customer_account_movements WHERE customer_id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("balance") : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al calcular saldo de cliente", e);
        }
    }

    public double getTotalCurrentBalance() {
        String sql = "SELECT COALESCE(SUM(CASE " +
                "WHEN type = 'DEBITO' THEN amount " +
                "WHEN type = 'CREDITO' THEN -amount " +
                "ELSE amount END), 0) " +
                "FROM customer_account_movements";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error al calcular saldo total de clientes", e);
        }
    }

    public List<CustomerDebtRow> getSalesDebtRows(int customerId) {
        String sql = "SELECT s.id AS sale_id, s.date AS sale_date, s.total AS sale_total, " +
                "COALESCE(SUM(a.applied_amount), 0) AS applied_amount " +
                "FROM sales s " +
                "LEFT JOIN customer_payment_applications a ON a.sale_id = s.id " +
                "WHERE s.customer_id = ? AND (s.payment_method = ? OR s.payment_method = ?) " +
                "GROUP BY s.id, s.date, s.total " +
                "ORDER BY datetime(s.date), s.id";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setString(2, PAYMENT_CURRENT_ACCOUNT);
            stmt.setString(3, PAYMENT_CURRENT_ACCOUNT_LEGACY);
            try (ResultSet rs = stmt.executeQuery()) {
                List<CustomerDebtRow> rows = new ArrayList<>();
                while (rs.next()) {
                    double total = rs.getDouble("sale_total");
                    double applied = rs.getDouble("applied_amount");
                    double pending = Math.max(0, total - applied);
                    rows.add(new CustomerDebtRow(
                            rs.getInt("sale_id"),
                            rs.getString("sale_date"),
                            total,
                            applied,
                            pending
                    ));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener deuda de cliente", e);
        }
    }

    public List<CustomerAccountMovementRow> getMovements(int customerId) {
        String sql = "SELECT id, date, type, amount, sale_id, payment_id, notes " +
                "FROM customer_account_movements WHERE customer_id = ? " +
                "ORDER BY datetime(date), id";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<CustomerAccountMovementRow> rows = new ArrayList<>();
                double running = 0;
                while (rs.next()) {
                    String type = rs.getString("type");
                    double amount = rs.getDouble("amount");
                    if ("DEBITO".equals(type)) {
                        running += amount;
                    } else if ("CREDITO".equals(type)) {
                        running -= amount;
                    }
                    int saleIdRaw = rs.getInt("sale_id");
                    Integer saleId = rs.wasNull() ? null : saleIdRaw;
                    int paymentIdRaw = rs.getInt("payment_id");
                    Integer paymentId = rs.wasNull() ? null : paymentIdRaw;
                    rows.add(new CustomerAccountMovementRow(
                            rs.getInt("id"),
                            rs.getString("date"),
                            type,
                            amount,
                            saleId,
                            paymentId,
                            rs.getString("notes"),
                            running
                    ));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener movimientos de cuenta corriente", e);
        }
    }

    public Map<Integer, String> getSaleDetailsBySaleIds(List<Integer> saleIds) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (saleIds == null || saleIds.isEmpty()) {
            return result;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < saleIds.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }

        String sql = "SELECT si.sale_id, p.name, si.quantity, si.price " +
                "FROM sale_items si " +
                "JOIN products p ON p.id = si.product_id " +
                "WHERE si.sale_id IN (" + placeholders + ") " +
                "ORDER BY si.sale_id, si.id";

        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Integer saleId : saleIds) {
                stmt.setInt(idx++, saleId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int saleId = rs.getInt("sale_id");
                    String name = rs.getString("name");
                    double qty = rs.getDouble("quantity");
                    double price = rs.getDouble("price");
                    String line = safe(name) + " x" + formatQty(qty) + " (" + formatMoney(qty * price) + ")";
                    String prev = result.get(saleId);
                    result.put(saleId, prev == null ? line : (prev + " | " + line));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener detalle de ventas", e);
        }
    }

    private int insertPayment(int customerId, String date, double amount, String paymentMethod, String notes) {
        String sql = "INSERT INTO customer_payments(customer_id, date, amount, payment_method, notes) VALUES (?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, customerId);
            stmt.setString(2, date);
            stmt.setDouble(3, amount);
            stmt.setString(4, paymentMethod);
            stmt.setString(5, notes);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            throw new RuntimeException("No se pudo generar ID de pago");
        } catch (SQLException e) {
            throw new RuntimeException("Error al registrar pago", e);
        }
    }

    private void insertCreditMovement(int customerId, String date, double amount, int paymentId, String notes) {
        String sql = "INSERT INTO customer_account_movements(customer_id, date, type, amount, sale_id, payment_id, notes) " +
                "VALUES (?, ?, 'CREDITO', ?, NULL, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setString(2, date);
            stmt.setDouble(3, amount);
            stmt.setInt(4, paymentId);
            String movementNote = (notes == null || notes.isBlank()) ? "Pago de cuenta corriente" : notes.trim();
            stmt.setString(5, movementNote);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al registrar movimiento de pago", e);
        }
    }

    private void applyPaymentFifo(int customerId, int paymentId, double amount) {
        double remaining = amount;
        for (PendingSale sale : fetchPendingSales(customerId)) {
            if (remaining <= 0.000001d) {
                break;
            }
            double apply = Math.min(remaining, sale.pendingAmount);
            insertPaymentApplication(paymentId, sale.saleId, apply);
            remaining -= apply;
        }
    }

    private List<PendingSale> fetchPendingSales(int customerId) {
        String sql = "SELECT s.id AS sale_id, s.total AS sale_total, COALESCE(SUM(a.applied_amount), 0) AS applied_amount " +
                "FROM sales s " +
                "LEFT JOIN customer_payment_applications a ON a.sale_id = s.id " +
                "WHERE s.customer_id = ? AND (s.payment_method = ? OR s.payment_method = ?) " +
                "GROUP BY s.id, s.date, s.total " +
                "HAVING (s.total - COALESCE(SUM(a.applied_amount), 0)) > 0.000001 " +
                "ORDER BY datetime(s.date), s.id";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setString(2, PAYMENT_CURRENT_ACCOUNT);
            stmt.setString(3, PAYMENT_CURRENT_ACCOUNT_LEGACY);
            try (ResultSet rs = stmt.executeQuery()) {
                List<PendingSale> rows = new ArrayList<>();
                while (rs.next()) {
                    double total = rs.getDouble("sale_total");
                    double applied = rs.getDouble("applied_amount");
                    rows.add(new PendingSale(rs.getInt("sale_id"), Math.max(0, total - applied)));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar ventas pendientes", e);
        }
    }

    private void insertPaymentApplication(int paymentId, int saleId, double appliedAmount) {
        String sql = "INSERT INTO customer_payment_applications(payment_id, sale_id, applied_amount) VALUES (?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, paymentId);
            stmt.setInt(2, saleId);
            stmt.setDouble(3, appliedAmount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al aplicar pago a venta", e);
        }
    }

    public void deletePayment(int paymentId) {
        Connection conn = DatabaseManager.getConnection();
        DatabaseManager.runInTransaction(() -> {
            // Eliminar aplicaciones del pago (libera la deuda de las ventas)
            String deleteApps = "DELETE FROM customer_payment_applications WHERE payment_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteApps)) {
                stmt.setInt(1, paymentId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error al eliminar aplicaciones del pago", e);
            }
            // Eliminar movimiento CREDITO del historial
            String deleteMovement = "DELETE FROM customer_account_movements WHERE payment_id = ? AND type = 'CREDITO'";
            try (PreparedStatement stmt = conn.prepareStatement(deleteMovement)) {
                stmt.setInt(1, paymentId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error al eliminar movimiento del pago", e);
            }
            // Eliminar el registro del pago
            String deletePayment = "DELETE FROM customer_payments WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deletePayment)) {
                stmt.setInt(1, paymentId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error al eliminar registro de pago", e);
            }
        });
    }

    public void deleteManualMovement(int movementId) {
        String checkSql = "SELECT sale_id, payment_id FROM customer_account_movements WHERE id = ?";
        String deleteSql = "DELETE FROM customer_account_movements WHERE id = ? AND sale_id IS NULL AND payment_id IS NULL";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setInt(1, movementId);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Movimiento no encontrado.");
                if (rs.getObject("sale_id") != null || rs.getObject("payment_id") != null) {
                    throw new IllegalArgumentException("Solo se pueden eliminar ajustes manuales (deuda anterior). Las ventas y pagos se gestionan desde sus propias secciones.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al verificar movimiento", e);
        }
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, movementId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar movimiento", e);
        }
    }

    public int cleanupOrphanedCreditMovements() {
        Connection conn = DatabaseManager.getConnection();
        try {
            int total = 0;

            // 1. Borrar aplicaciones que apuntan a ventas eliminadas
            String cleanAppsSales = "DELETE FROM customer_payment_applications " +
                    "WHERE sale_id NOT IN (SELECT id FROM sales)";
            try (PreparedStatement stmt = conn.prepareStatement(cleanAppsSales)) {
                int n = stmt.executeUpdate();
                if (n > 0) AppLogger.info("CurrentAccountService", "cleanup", "Apps de ventas eliminadas: " + n);
                total += n;
            }

            // 2. Borrar aplicaciones que apuntan a pagos sin movimiento CREDITO
            //    (pagos fantasma que quedaron después de una limpieza anterior)
            String cleanAppsPhantom = "DELETE FROM customer_payment_applications " +
                    "WHERE payment_id NOT IN (" +
                    "  SELECT DISTINCT payment_id FROM customer_account_movements " +
                    "  WHERE payment_id IS NOT NULL AND type = 'CREDITO')";
            try (PreparedStatement stmt = conn.prepareStatement(cleanAppsPhantom)) {
                int n = stmt.executeUpdate();
                if (n > 0) AppLogger.info("CurrentAccountService", "cleanup", "Apps de pagos fantasma eliminadas: " + n);
                total += n;
            }

            // 3. Borrar movimientos CREDITO para pagos sin aplicaciones restantes
            String cleanMovements = "DELETE FROM customer_account_movements " +
                    "WHERE type = 'CREDITO' AND payment_id IS NOT NULL " +
                    "AND payment_id NOT IN " +
                    "(SELECT DISTINCT payment_id FROM customer_payment_applications WHERE payment_id IS NOT NULL)";
            try (PreparedStatement stmt = conn.prepareStatement(cleanMovements)) {
                int n = stmt.executeUpdate();
                if (n > 0) AppLogger.info("CurrentAccountService", "cleanup", "Movimientos CREDITO huerfanos: " + n);
                total += n;
            }

            // 4. Borrar registros de customer_payments sin movimiento CREDITO
            //    (evita que applyAvailableCreditsToSale los aplique como saldo disponible)
            String cleanPhantomPayments = "DELETE FROM customer_payments " +
                    "WHERE id NOT IN (" +
                    "  SELECT DISTINCT payment_id FROM customer_account_movements " +
                    "  WHERE payment_id IS NOT NULL AND type = 'CREDITO')";
            try (PreparedStatement stmt = conn.prepareStatement(cleanPhantomPayments)) {
                int n = stmt.executeUpdate();
                if (n > 0) AppLogger.info("CurrentAccountService", "cleanup", "Pagos fantasma eliminados: " + n);
                total += n;
            }

            return total;
        } catch (SQLException e) {
            AppLogger.error("CurrentAccountService", "cleanupOrphanedCreditMovements",
                    "Error al limpiar movimientos huerfanos", e);
            throw new RuntimeException("Error al limpiar movimientos de cuenta corriente", e);
        }
    }

    private static final class PendingSale {
        private final int saleId;
        private final double pendingAmount;

        private PendingSale(int saleId, double pendingAmount) {
            this.saleId = saleId;
            this.pendingAmount = pendingAmount;
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String formatQty(double value) {
        long asLong = (long) value;
        if (Math.abs(value - asLong) < 0.000001d) {
            return String.valueOf(asLong);
        }
        return String.format("%.2f", value).replace('.', ',');
    }

    private static String formatMoney(double total) {
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
}
