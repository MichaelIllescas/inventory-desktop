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

    /** Total de productos visibles (excluye __VARIOS__). */
    int countAll();

    /** Total de productos con stock bajo o igual al mínimo (excluye __VARIOS__). */
    int countLowStock();

    /** Total de productos que coinciden con la búsqueda. */
    int countSearch(String query);

    /** Página de productos sin filtro. offset = página * pageSize. */
    List<Product> findPage(int offset, int pageSize);

    /** Página de productos filtrados por búsqueda. */
    List<Product> searchPage(String query, int offset, int pageSize);
}

