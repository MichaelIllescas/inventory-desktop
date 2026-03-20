package com.ferreteria.services;

import com.ferreteria.models.Product;
import com.ferreteria.models.ReplenishmentItem;
import com.ferreteria.repositories.ProductRepository;

import java.util.List;

public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> getAllProducts() {
        return repository.findAll();
    }

    public List<Product> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll();
        }
        return repository.search(query.trim());
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
        return repository.findLowStock();
    }

    public List<ReplenishmentItem> getReplenishmentList() {
        return repository.findReplenishmentList();
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
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
}

