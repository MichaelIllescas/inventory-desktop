package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Product;
import com.ferreteria.models.Sale;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SaleLineItem;
import com.ferreteria.repositories.ProductRepository;
import com.ferreteria.repositories.SaleRepository;
import com.ferreteria.util.AppLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SaleService {
    private static final String PAYMENT_CURRENT_ACCOUNT = "Cuenta corriente";
    private static final String PAYMENT_CURRENT_ACCOUNT_LEGACY = "CUENTA_CORRIENTE";

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
    }

    public Optional<Product> findProductByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return productRepository.findByCode(code.trim());
    }

    /**
     * Registra la venta: crea la venta, guarda items y descuenta stock en una transaccion.
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod) {
        double total = lines.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        registerSale(lines, paymentMethod, total, null, null);
    }

    /**
     * Registra la venta con un total personalizado.
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal) {
        registerSale(lines, paymentMethod, customTotal, null, null);
    }

    /**
     * Registra la venta con trazabilidad por operationId.
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal, String operationId) {
        registerSale(lines, paymentMethod, customTotal, null, operationId);
    }

    /**
     * Registra venta opcionalmente asociada a un cliente.
     * Si paymentMethod es CUENTA_CORRIENTE, customerId es obligatorio y se genera DEBITO.
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal, Integer customerId, String operationId) {
        if (operationId != null) AppLogger.setOperationId(operationId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("No hay items en la venta.");
        }
        boolean isCurrentAccount = PAYMENT_CURRENT_ACCOUNT.equals(paymentMethod)
                || PAYMENT_CURRENT_ACCOUNT_LEGACY.equals(paymentMethod);
        if (isCurrentAccount && customerId == null) {
            throw new IllegalArgumentException("Debe seleccionar un cliente para cuenta corriente.");
        }
        if (isCurrentAccount) {
            validateCreditLimit(customerId, customTotal);
        }

        AppLogger.info("SaleService", "registerSale",
                "Validando " + lines.size() + " items, total=" + customTotal + ", pago=" + paymentMethod + ", customerId=" + customerId);

        List<SaleItem> items = new ArrayList<>();
        for (SaleLineItem line : lines) {
            Product p = productRepository.findById(line.getProductId()).orElseThrow();
            if (!p.isSkipStock() && p.getStock() < line.getQuantity()) {
                AppLogger.warn("SaleService", "registerSale",
                        "Stock insuficiente: producto=" + p.getName() + " stock=" + p.getStock() + " pedido=" + line.getQuantity());
                throw new IllegalArgumentException("Stock insuficiente para '" + p.getName() + "'. Disponible: " + p.getStock());
            }
            items.add(new SaleItem(null, line.getProductId(), line.getQuantity(), line.getEffectiveUnitPrice()));
        }

        Sale sale = new Sale();
        sale.setDate(Sale.nowAsString());
        sale.setTotal(customTotal);
        sale.setPaymentMethod(paymentMethod);
        sale.setCustomerId(customerId);

        AppLogger.info("SaleService", "registerSale", "Iniciando transaccion en BD");
        DatabaseManager.runInTransaction(() -> {
            Sale saved = saleRepository.saveSale(sale);
            AppLogger.info("SaleService", "registerSale", "Venta guardada con id=" + saved.getId());
            saleRepository.saveSaleItems(saved.getId(), items);

            if (isCurrentAccount) {
                insertCurrentAccountDebit(customerId, saved.getId(), sale.getDate(), customTotal);
                applyAvailableCreditsToSale(customerId, saved.getId(), customTotal);
            }

            for (SaleLineItem line : lines) {
                Product p = productRepository.findById(line.getProductId()).orElseThrow();
                if (!p.isSkipStock()) {
                    productRepository.decreaseStock(line.getProductId(), line.getQuantity());
                    AppLogger.info("SaleService", "registerSale",
                            "Stock descontado: productoId=" + line.getProductId() + " cantidad=" + line.getQuantity());
                }
            }
        });
        AppLogger.info("SaleService", "registerSale", "Transaccion completada OK");
    }

    private void validateCreditLimit(int customerId, double saleAmount) {
        double creditLimit = getCustomerCreditLimit(customerId);
        if (creditLimit < 0) {
            return; // Sin limite
        }
        double currentBalance = getCustomerCurrentBalance(customerId);
        double projectedBalance = currentBalance + saleAmount;
        if (projectedBalance - creditLimit > 0.000001d) {
            double remaining = Math.max(0d, creditLimit - currentBalance);
            throw new IllegalArgumentException(
                    "Limite de cuenta corriente excedido.\n" +
                            "Limite: " + formatCurrency(creditLimit) + "\n" +
                            "Deuda actual: " + formatCurrency(currentBalance) + "\n" +
                            "Restan: " + formatCurrency(remaining)
            );
        }
    }

    private double getCustomerCreditLimit(int customerId) {
        String sql = "SELECT credit_limit FROM customers WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Cliente no encontrado.");
                }
                return rs.getDouble("credit_limit");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener limite de credito del cliente", e);
        }
    }

    private double getCustomerCurrentBalance(int customerId) {
        String sql = "SELECT COALESCE(SUM(CASE " +
                "WHEN type = 'DEBITO' THEN amount " +
                "WHEN type = 'CREDITO' THEN -amount " +
                "ELSE 0 END), 0) AS balance " +
                "FROM customer_account_movements WHERE customer_id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("balance") : 0d;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener saldo actual del cliente", e);
        }
    }

    private void insertCurrentAccountDebit(int customerId, int saleId, String date, double amount) {
        String sql = "INSERT INTO customer_account_movements(customer_id, date, type, amount, sale_id, payment_id, notes) " +
                "VALUES (?, ?, 'DEBITO', ?, ?, NULL, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            stmt.setString(2, date);
            stmt.setDouble(3, amount);
            stmt.setInt(4, saleId);
            stmt.setString(5, "Venta Nro " + saleId + " a cuenta corriente");
            stmt.executeUpdate();
            AppLogger.info("SaleService", "insertCurrentAccountDebit",
                    "Debito registrado: customerId=" + customerId + " saleId=" + saleId + " amount=" + amount);
        } catch (SQLException e) {
            throw new RuntimeException("Error al registrar debito de cuenta corriente", e);
        }
    }

    private void applyAvailableCreditsToSale(int customerId, int saleId, double saleTotal) {
        if (saleTotal <= 0.000001d) {
            return;
        }
        String sql = "SELECT cp.id AS payment_id, " +
                "       (cp.amount - COALESCE(SUM(cpa.applied_amount), 0)) AS available " +
                "FROM customer_payments cp " +
                "LEFT JOIN customer_payment_applications cpa ON cpa.payment_id = cp.id " +
                "WHERE cp.customer_id = ? " +
                "GROUP BY cp.id, cp.date, cp.amount " +
                "HAVING (cp.amount - COALESCE(SUM(cpa.applied_amount), 0)) > 0.000001 " +
                "ORDER BY datetime(cp.date), cp.id";

        Connection conn = DatabaseManager.getConnection();
        double remaining = saleTotal;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next() && remaining > 0.000001d) {
                    int paymentId = rs.getInt("payment_id");
                    double available = rs.getDouble("available");
                    double apply = Math.min(remaining, available);
                    insertPaymentApplication(paymentId, saleId, apply);
                    remaining -= apply;
                }
            }
            if (remaining < saleTotal) {
                AppLogger.info("SaleService", "applyAvailableCreditsToSale",
                        "Saldo a favor aplicado a venta: customerId=" + customerId + " saleId=" + saleId +
                                " aplicado=" + (saleTotal - remaining));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al aplicar saldo a favor a la venta", e);
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
            throw new RuntimeException("Error al registrar aplicacion de pago a venta", e);
        }
    }

    /**
     * Anula una venta: devuelve el stock de cada item y elimina la venta (y sus items por CASCADE).
     */
    public void deleteSale(int saleId) {
        String opId = AppLogger.beginOperation();
        try {
            List<SaleItem> items = saleRepository.getSaleItemsBySaleId(saleId);
            AppLogger.info("SaleService", "deleteSale",
                    "Anulando venta id=" + saleId + " con " + items.size() + " items");
            DatabaseManager.runInTransaction(() -> {
                deletePaymentApplicationsForSale(saleId);
                deleteCurrentAccountSaleMovements(saleId);
                for (SaleItem item : items) {
                    productRepository.increaseStock(item.getProductId(), item.getQuantity());
                    AppLogger.info("SaleService", "deleteSale",
                            "Stock devuelto: productoId=" + item.getProductId() + " cantidad=" + item.getQuantity());
                }
                saleRepository.deleteSale(saleId);
            });
            AppLogger.info("SaleService", "deleteSale", "Venta anulada correctamente: id=" + saleId);
        } catch (Exception e) {
            AppLogger.error("SaleService", "deleteSale", "Error al anular venta id=" + saleId, e);
            throw e;
        } finally {
            AppLogger.endOperation();
        }
    }

    private void deletePaymentApplicationsForSale(int saleId) {
        Connection conn = DatabaseManager.getConnection();

        // Pagos que estaban aplicados a esta venta
        List<Integer> paymentIds = new ArrayList<>();
        String fetchSql = "SELECT DISTINCT payment_id FROM customer_payment_applications WHERE sale_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(fetchSql)) {
            stmt.setInt(1, saleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) paymentIds.add(rs.getInt("payment_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener pagos aplicados a la venta", e);
        }

        // Eliminar las aplicaciones de esta venta
        String deleteAppsSql = "DELETE FROM customer_payment_applications WHERE sale_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteAppsSql)) {
            stmt.setInt(1, saleId);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                AppLogger.info("SaleService", "deletePaymentApplicationsForSale",
                        "Aplicaciones eliminadas para saleId=" + saleId + ": " + deleted);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar aplicaciones de pago de la venta", e);
        }

        // Para cada pago: si ya no tiene aplicaciones en ninguna venta, revertir su movimiento y el registro
        for (int paymentId : paymentIds) {
            String checkSql = "SELECT COUNT(*) FROM customer_payment_applications WHERE payment_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setInt(1, paymentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        // Pago sin más aplicaciones → revertir movimiento de cuenta corriente
                        String delMovementSql = "DELETE FROM customer_account_movements WHERE payment_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(delMovementSql)) {
                            ps.setInt(1, paymentId);
                            ps.executeUpdate();
                        }
                        AppLogger.info("SaleService", "deletePaymentApplicationsForSale",
                                "Movimiento de pago revertido para paymentId=" + paymentId);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error al revertir movimiento de pago", e);
            }
        }
    }

    private String formatCurrency(double total) {
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

    private void deleteCurrentAccountSaleMovements(int saleId) {
        String sql = "DELETE FROM customer_account_movements WHERE sale_id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                AppLogger.info("SaleService", "deleteCurrentAccountSaleMovements",
                        "Movimientos de cuenta corriente eliminados para saleId=" + saleId + ": " + deleted);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al limpiar movimientos de cuenta corriente de la venta", e);
        }
    }
}
