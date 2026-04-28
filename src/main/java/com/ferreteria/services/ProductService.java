package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Product;
import com.ferreteria.models.ReplenishmentItem;
import com.ferreteria.repositories.ProductRepository;

import java.util.List;
import java.util.Optional;

public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> getAllProducts() {
        return repository.findAll().stream()
                .filter(this::isVisibleProduct)
                .toList();
    }

    public List<Product> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return getAllProducts();
        }
        return repository.search(query.trim()).stream()
                .filter(this::isVisibleProduct)
                .toList();
    }

    public Product saveProduct(Product product) {
        validateProduct(product);
        return repository.save(product);
    }

    public void deleteProduct(Product product) {
        if (product.getId() != null) {
            repository.deleteById(product.getId());
        }
    }

    public List<Product> getLowStockProducts() {
        return repository.findLowStock().stream()
                .filter(this::isVisibleProduct)
                .toList();
    }

    public List<ReplenishmentItem> getReplenishmentList() {
        return repository.findReplenishmentList().stream()
                .filter(this::isVisibleReplenishmentItem)
                .toList();
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (product.getCode() != null) {
            String code = product.getCode().trim();
            if (!code.isEmpty()) {
                Optional<Product> existing = repository.findByCode(code);
                boolean duplicated = existing.isPresent()
                        && !existing.get().isPrecarga()
                        && (product.getId() == null || !product.getId().equals(existing.get().getId()));
                if (duplicated) {
                    throw new IllegalArgumentException("Ya existe un producto con ese código.");
                }
            }
        }
        if (product.getPrice() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo.");
        }
        if (product.getStock() < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        if (product.getMinimumStock() < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        }
    }

    private boolean isVisibleProduct(Product product) {
        return product != null
                && !product.isSkipStock()
                && !DatabaseManager.VARIOS_CODE.equals(product.getCode());
    }

    private boolean isVisibleReplenishmentItem(ReplenishmentItem item) {
        return item != null
                && !DatabaseManager.VARIOS_CODE.equals(item.getProductCode());
    }
}