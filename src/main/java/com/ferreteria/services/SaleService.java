package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Product;
import com.ferreteria.models.Sale;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SaleLineItem;
import com.ferreteria.repositories.ProductRepository;
import com.ferreteria.repositories.SaleRepository;

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
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("No hay ítems en la venta.");
        }
        double total = 0;
        List<SaleItem> items = new ArrayList<>();
        for (SaleLineItem line : lines) {
            Product p = productRepository.findById(line.getProductId()).orElseThrow();
            if (p.getStock() < line.getQuantity()) {
                throw new IllegalArgumentException("Stock insuficiente para '" + p.getName() + "'. Disponible: " + p.getStock());
            }
            total += line.getSubtotal();
            items.add(new SaleItem(null, line.getProductId(), line.getQuantity(), line.getUnitPrice()));
        }
        Sale sale = new Sale();
        sale.setDate(Sale.nowAsString());
        sale.setTotal(total);
        sale.setPaymentMethod(paymentMethod);

        DatabaseManager.runInTransaction(() -> {
            Sale saved = saleRepository.saveSale(sale);
            saleRepository.saveSaleItems(saved.getId(), items);
            for (SaleLineItem line : lines) {
                productRepository.decreaseStock(line.getProductId(), line.getQuantity());
            }
        });
    }

    /**
     * Anula una venta: devuelve el stock de cada ítem y elimina la venta (y sus ítems por CASCADE).
     */
    public void deleteSale(int saleId) {
        List<SaleItem> items = saleRepository.getSaleItemsBySaleId(saleId);
        DatabaseManager.runInTransaction(() -> {
            for (SaleItem item : items) {
                productRepository.increaseStock(item.getProductId(), item.getQuantity());
            }
            saleRepository.deleteSale(saleId);
        });
    }
}
