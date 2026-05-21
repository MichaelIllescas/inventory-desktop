package com.ferreteria.repositories;

import com.ferreteria.models.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(int id);

    Optional<Customer> findActiveByNameExact(String name);

    List<Customer> findAllActive();

    List<Customer> searchActive(String query);

    double getPendingDebtByCustomerId(int customerId);

    void deactivateById(int id);
}
