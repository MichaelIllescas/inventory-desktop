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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SaleService {
    private static final String PAYMENT_CURRENT_ACCOUNT = "Cuenta corriente";
    private static final String PAYMENT_CURRENT_ACCOUNT_LEGACY = "CUENTA_CORRIENTE";

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final LicenseService licenseService;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.licenseService = new LicenseService();
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
    public int registerSale(List<SaleLineItem> lines, String paymentMethod) {
        double total = lines.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        return registerSale(lines, paymentMethod, total, null, null);
    }

    /**
     * Registra la venta con un total personalizado.
     */
    public int registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal) {
        return registerSale(lines, paymentMethod, customTotal, null, null);
    }

    /**
     * Registra la venta con trazabilidad por operationId.
     */
    public int registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal, String operationId) {
        return registerSale(lines, paymentMethod, customTotal, null, operationId);
    }

    /**
     * Registra venta opcionalmente asociada a un cliente.
     * Si paymentMethod es CUENTA_CORRIENTE, customerId es obligatorio y se genera DEBITO.
     */
    public int registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal, Integer customerId, String operationId) {
        if (operationId != null) AppLogger.setOperationId(operationId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("No hay items en la venta.");
        }
        boolean isCurrentAccount = PAYMENT_CURRENT_ACCOUNT.equals(paymentMethod)
                || PAYMENT_CURRENT_ACCOUNT_LEGACY.equals(paymentMethod);
        if (isCurrentAccount && !licenseService.isCurrentAccountsEnabled()) {
            throw new IllegalArgumentException("El módulo de cuentas corrientes no está habilitado en esta edición.");
        }
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

        // El descuento/aumento aplicado sobre el total se reparte entre los ítems, para que el precio
        // guardado en cada línea sea el realmente cobrado (reportes por producto, ediciones posteriores).
        final List<SaleItem> itemsToSave = distributeTotalAmongItems(items, customTotal);

        Sale sale = new Sale();
        sale.setDate(Sale.nowAsString());
        sale.setTotal(customTotal);
        sale.setPaymentMethod(paymentMethod);
        sale.setCustomerId(customerId);

        AppLogger.info("SaleService", "registerSale", "Iniciando transaccion en BD");
        // El id se necesita fuera de la transaccion para poder imprimir el ticket.
        final int[] savedId = new int[1];
        DatabaseManager.runInTransaction(() -> {
            Sale saved = saleRepository.saveSale(sale);
            savedId[0] = saved.getId();
            AppLogger.info("SaleService", "registerSale", "Venta guardada con id=" + saved.getId());
            saleRepository.saveSaleItems(saved.getId(), itemsToSave);

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
        return savedId[0];
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

    public void updateSale(int saleId, String newDate, List<SaleItem> newItems) {
        updateSale(saleId, newDate, newItems, null);
    }

    /**
     * Actualiza la venta. Si customTotal es null, el total se recalcula como la suma de los ítems;
     * si viene informado, se respeta el total indicado (permite conservar descuentos/ajustes
     * aplicados sobre el total de la venta).
     */
    public void updateSale(int saleId, String newDate, List<SaleItem> newItems, Double customTotal) {
        if (saleId <= 0) {
            throw new IllegalArgumentException("Venta inválida.");
        }
        if (newDate == null || newDate.isBlank()) {
            throw new IllegalArgumentException("La fecha y hora son obligatorias.");
        }
        if (newItems == null || newItems.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un ítem.");
        }
        for (SaleItem item : newItems) {
            if (item.getProductId() == null || item.getProductId() <= 0) {
                throw new IllegalArgumentException("Hay un ítem sin producto válido.");
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Las cantidades deben ser mayores a 0.");
            }
            if (item.getPrice() < 0) {
                throw new IllegalArgumentException("Los precios no pueden ser negativos.");
            }
        }

        double itemsTotal = newItems.stream().mapToDouble(SaleItem::getSubtotal).sum();
        if (customTotal != null && customTotal < 0) {
            throw new IllegalArgumentException("El total de la venta no puede ser negativo.");
        }
        double newTotal = customTotal != null ? customTotal : itemsTotal;
        final List<SaleItem> itemsToSave = distributeTotalAmongItems(newItems, newTotal);

        DatabaseManager.runInTransaction(() -> {
            SaleSnapshot snapshot = getSaleSnapshot(saleId);
            List<SaleItem> oldItems = saleRepository.getSaleItemsBySaleId(saleId);
            validateStockForSaleUpdate(oldItems, newItems);

            updateSaleHeader(saleId, newDate, newTotal);
            replaceSaleItems(saleId, itemsToSave);

            if (isCurrentAccountPayment(snapshot.paymentMethod)) {
                if (snapshot.customerId == null) {
                    throw new IllegalStateException("La venta en cuenta corriente no tiene cliente asociado.");
                }
                updateCurrentAccountDebitForSale(saleId, newDate, newTotal);
                deletePaymentApplicationsForSale(saleId);
                applyAvailableCreditsToSale(snapshot.customerId, saleId, newTotal);
            }

            Map<Integer, Double> previousByProduct = sumQuantitiesByProduct(oldItems);
            Map<Integer, Double> requestedByProduct = sumQuantitiesByProduct(newItems);
            for (Map.Entry<Integer, Double> entry : requestedByProduct.entrySet()) {
                Product product = productRepository.findById(entry.getKey()).orElseThrow();
                if (!product.isSkipStock()) {
                    double previousQuantity = previousByProduct.getOrDefault(entry.getKey(), 0d);
                    double delta = entry.getValue() - previousQuantity;
                    if (delta > 0) {
                        productRepository.decreaseStock(entry.getKey(), delta);
                    } else if (delta < 0) {
                        productRepository.increaseStock(entry.getKey(), Math.abs(delta));
                    }
                }
            }
            for (Map.Entry<Integer, Double> entry : previousByProduct.entrySet()) {
                if (!requestedByProduct.containsKey(entry.getKey())) {
                    Product product = productRepository.findById(entry.getKey()).orElseThrow();
                    if (!product.isSkipStock()) {
                        productRepository.increaseStock(entry.getKey(), entry.getValue());
                    }
                }
            }
        });
    }

    /**
     * Reparte proporcionalmente la diferencia entre el total cobrado y la suma de los ítems,
     * de modo que el precio unitario guardado en cada línea sea el efectivamente cobrado.
     * El resto por redondeo se absorbe en la última línea, así sum(cantidad * precio) == total.
     * Las cantidades nunca se modifican (el stock no se ve afectado).
     */
    private static List<SaleItem> distributeTotalAmongItems(List<SaleItem> items, double targetTotal) {
        double itemsSum = items.stream().mapToDouble(SaleItem::getSubtotal).sum();
        if (itemsSum <= 0 || targetTotal < 0 || Math.abs(targetTotal - itemsSum) < 0.005) {
            return items;
        }

        double factor = targetTotal / itemsSum;
        List<SaleItem> adjusted = new ArrayList<>(items.size());
        double accumulated = 0;
        for (int i = 0; i < items.size(); i++) {
            SaleItem item = items.get(i);
            double subtotal;
            if (i == items.size() - 1) {
                subtotal = round2(targetTotal - accumulated);
            } else {
                subtotal = round2(item.getSubtotal() * factor);
                accumulated += subtotal;
            }
            double price = item.getQuantity() > 0 ? subtotal / item.getQuantity() : 0d;
            adjusted.add(new SaleItem(item.getSaleId(), item.getProductId(), item.getQuantity(), price));
        }
        return adjusted;
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private void validateStockForSaleUpdate(List<SaleItem> oldItems, List<SaleItem> newItems) {
        Map<Integer, Double> previousByProduct = sumQuantitiesByProduct(oldItems);
        Map<Integer, Double> requestedByProduct = sumQuantitiesByProduct(newItems);

        for (Map.Entry<Integer, Double> entry : requestedByProduct.entrySet()) {
            Product product = productRepository.findById(entry.getKey()).orElseThrow();
            if (product.isSkipStock()) {
                continue;
            }
            double previousQuantity = previousByProduct.getOrDefault(entry.getKey(), 0d);
            double availableForThisSale = product.getStock() + previousQuantity;
            double requestedQuantity = entry.getValue();
            if (availableForThisSale < requestedQuantity) {
                throw new IllegalArgumentException("Stock insuficiente para '" + product.getName()
                        + "'. Disponible: " + availableForThisSale);
            }
        }
    }

    private Map<Integer, Double> sumQuantitiesByProduct(List<SaleItem> items) {
        Map<Integer, Double> quantities = new HashMap<>();
        for (SaleItem item : items) {
            quantities.merge(item.getProductId(), item.getQuantity(), Double::sum);
        }
        return quantities;
    }

    private boolean isCurrentAccountPayment(String paymentMethod) {
        return PAYMENT_CURRENT_ACCOUNT.equals(paymentMethod) || PAYMENT_CURRENT_ACCOUNT_LEGACY.equals(paymentMethod);
    }

    private SaleSnapshot getSaleSnapshot(int saleId) {
        String sql = "SELECT payment_method, customer_id FROM sales WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Venta no encontrada.");
                }
                int customerIdRaw = rs.getInt("customer_id");
                Integer customerId = rs.wasNull() ? null : customerIdRaw;
                return new SaleSnapshot(rs.getString("payment_method"), customerId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener la venta", e);
        }
    }

    private void updateSaleHeader(int saleId, String date, double total) {
        String sql = "UPDATE sales SET date = ?, total = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setDouble(2, total);
            stmt.setInt(3, saleId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar la venta", e);
        }
    }

    private void replaceSaleItems(int saleId, List<SaleItem> items) {
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM sale_items WHERE sale_id = ?")) {
            delete.setInt(1, saleId);
            delete.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al reemplazar ítems de venta", e);
        }

        String sql = "INSERT INTO sale_items(sale_id, product_id, quantity, price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (SaleItem item : items) {
                stmt.setInt(1, saleId);
                stmt.setInt(2, item.getProductId());
                stmt.setDouble(3, item.getQuantity());
                stmt.setDouble(4, item.getPrice());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar ítems de venta", e);
        }
    }

    private void updateCurrentAccountDebitForSale(int saleId, String date, double amount) {
        String sql = "UPDATE customer_account_movements SET date = ?, amount = ? WHERE sale_id = ? AND type = 'DEBITO'";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setDouble(2, amount);
            stmt.setInt(3, saleId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar movimiento de cuenta corriente", e);
        }
    }

    private record SaleSnapshot(String paymentMethod, Integer customerId) {}

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
