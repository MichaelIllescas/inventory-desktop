package com.ferreteria.services;

import com.ferreteria.models.Customer;
import com.ferreteria.repositories.CustomerRepository;

import java.util.Locale;
import java.util.List;
import java.util.Optional;

public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<Customer> getAllActiveCustomers() {
        return repository.findAllActive();
    }

    public Optional<Customer> findById(int id) {
        return repository.findById(id);
    }

    public List<Customer> searchActiveCustomers(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAllActive();
        }
        return repository.searchActive(query.trim());
    }

    public Customer createIfMissingByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio.");
        }
        String normalized = toTitleCase(name);
        Optional<Customer> existing = repository.findActiveByNameExact(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }
        Customer customer = new Customer();
        customer.setName(normalized);
        customer.setActive(true);
        customer.setCreditLimit(0);
        return repository.save(customer);
    }

    public Customer saveCustomer(Customer customer) {
        validate(customer);
        normalizeCustomerFields(customer);
        if (customer.getId() == null) {
            customer.setActive(true);
        }
        return repository.save(customer);
    }

    public void deactivateCustomer(Customer customer) {
        if (customer == null || customer.getId() == null) {
            return;
        }
        double pendingDebt = repository.getPendingDebtByCustomerId(customer.getId());
        if (pendingDebt > 0.000001d) {
            throw new IllegalArgumentException("No se puede eliminar/desactivar el cliente porque tiene deuda pendiente de " + formatCurrency(pendingDebt) + ".");
        }
        repository.deactivateById(customer.getId());
    }

    private void validate(Customer customer) {
        if (customer == null || customer.getName() == null || customer.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio.");
        }
    }

    private void normalizeCustomerFields(Customer customer) {
        customer.setName(toTitleCase(customer.getName()));
        customer.setAddress(toTitleCase(customer.getAddress()));
        if (customer.getPhone() != null) {
            customer.setPhone(customer.getPhone().trim());
        }
    }

    private String toTitleCase(String value) {
        if (value == null) {
            return null;
        }
        String input = value.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return null;
        }

        StringBuilder out = new StringBuilder(input.length());
        boolean nextUpper = true;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (Character.isLetter(ch)) {
                out.append(nextUpper ? Character.toUpperCase(ch) : ch);
                nextUpper = false;
            } else {
                out.append(ch);
                nextUpper = ch == ' ' || ch == '-' || ch == '/' || ch == '\'';
            }
        }
        return out.toString();
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
}
