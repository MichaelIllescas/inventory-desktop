package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.CustomerAccountMovementRow;
import com.ferreteria.models.CustomerDebtRow;

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
