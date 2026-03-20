package com.ferreteria.repositories;

import com.ferreteria.models.Product;
import com.ferreteria.models.ReplenishmentItem;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    void deleteById(int id);

    Optional<Product> findById(int id);

    List<Product> findAll();

    List<Product> search(String query);

    List<Product> findLowStock();

    /** Lista para reposición: productos con bajo stock y datos del proveedor. */
    List<ReplenishmentItem> findReplenishmentList();

    /** Busca un producto por código (exacto o que empiece con el texto). Para escáner de código de barras. */
    Optional<Product> findByCode(String code);

    void decreaseStock(int productId, double quantity);

    void increaseStock(int productId, double quantity);
}

