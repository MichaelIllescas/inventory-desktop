package com.ferreteria.services;

import com.ferreteria.models.Supplier;
import com.ferreteria.repositories.SupplierRepository;

import java.util.List;

public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    public List<Supplier> getAllSuppliers() {
        return repository.findAll();
    }

    public List<Supplier> searchSuppliers(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll();
        }
        return repository.search(query.trim());
    }

    public Supplier saveSupplier(Supplier supplier) {
        validateSupplier(supplier);
        return repository.save(supplier);
    }

    public void deleteSupplier(Supplier supplier) {
        if (supplier.getId() != null) {
            repository.deleteById(supplier.getId());
        }
    }

    private void validateSupplier(Supplier supplier) {
        if (supplier.getName() == null || supplier.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio.");
        }
    }
}
