package com.ferreteria.repositories;

import com.ferreteria.models.Supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository {

    Supplier save(Supplier supplier);

    void deleteById(int id);

    Optional<Supplier> findById(int id);

    List<Supplier> findAll();

    List<Supplier> search(String query);
}
