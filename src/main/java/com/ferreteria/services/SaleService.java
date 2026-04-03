package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Product;
import com.ferreteria.models.Sale;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SaleLineItem;
import com.ferreteria.repositories.ProductRepository;
import com.ferreteria.repositories.SaleRepository;
import com.ferreteria.util.AppLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SaleService {

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
     * Registra la venta: crea la venta, guarda ítems y descuenta stock en una transacción.
     * @param lines ítems de la venta
     * @param paymentMethod texto del medio de pago (ej. "Efectivo")
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod) {
        double total = lines.stream().mapToDouble(SaleLineItem::getSubtotal).sum();
        registerSale(lines, paymentMethod, total);
    }

    /**
     * Registra la venta con un total personalizado (permite descuentos o ajustes manuales).
     * @param lines ítems de la venta
     * @param paymentMethod texto del medio de pago (ej. "Efectivo")
     * @param customTotal total final a registrar (puede diferir de la suma de subtotales)
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal) {
        registerSale(lines, paymentMethod, customTotal, null);
    }

    /**
     * Registra la venta con trazabilidad por operationId.
     */
    public void registerSale(List<SaleLineItem> lines, String paymentMethod, double customTotal, String operationId) {
        if (operationId != null) AppLogger.setOperationId(operationId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("No hay ítems en la venta.");
        }
        AppLogger.info("SaleService", "registerSale",
                "Validando " + lines.size() + " ítems, total=" + customTotal + ", pago=" + paymentMethod);
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

        AppLogger.info("SaleService", "registerSale", "Iniciando transacción en BD");
        DatabaseManager.runInTransaction(() -> {
            Sale saved = saleRepository.saveSale(sale);
            AppLogger.info("SaleService", "registerSale", "Venta guardada con id=" + saved.getId());
            saleRepository.saveSaleItems(saved.getId(), items);
            for (SaleLineItem line : lines) {
                Product p = productRepository.findById(line.getProductId()).orElseThrow();
                if (!p.isSkipStock()) {
                    productRepository.decreaseStock(line.getProductId(), line.getQuantity());
                    AppLogger.info("SaleService", "registerSale",
                            "Stock descontado: productoId=" + line.getProductId() + " cantidad=" + line.getQuantity());
                }
            }
        });
        AppLogger.info("SaleService", "registerSale", "Transacción completada OK");
    }

    /**
     * Anula una venta: devuelve el stock de cada ítem y elimina la venta (y sus ítems por CASCADE).
     */
    public void deleteSale(int saleId) {
        String opId = AppLogger.beginOperation();
        try {
            List<SaleItem> items = saleRepository.getSaleItemsBySaleId(saleId);
            AppLogger.info("SaleService", "deleteSale",
                    "Anulando venta id=" + saleId + " con " + items.size() + " ítems");
            DatabaseManager.runInTransaction(() -> {
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
}
