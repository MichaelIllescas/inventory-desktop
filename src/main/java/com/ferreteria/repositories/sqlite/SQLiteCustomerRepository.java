package com.ferreteria.repositories.sqlite;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Customer;
import com.ferreteria.repositories.CustomerRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteCustomerRepository implements CustomerRepository {

    @Override
    public Customer save(Customer customer) {
        if (customer.getId() == null) {
            return insert(customer);
        }
        return update(customer);
    }

    @Override
    public Optional<Customer> findById(int id) {
        String sql = "SELECT * FROM customers WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar cliente por id", e);
        }
    }

    @Override
    public Optional<Customer> findActiveByNameExact(String name) {
        String sql = "SELECT * FROM customers WHERE active = 1 AND lower(trim(name)) = lower(trim(?)) LIMIT 1";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar cliente por nombre", e);
        }
    }

    @Override
    public List<Customer> findAllActive() {
        String sql = "SELECT * FROM customers WHERE active = 1 ORDER BY name";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Customer> customers = new ArrayList<>();
            while (rs.next()) customers.add(mapRow(rs));
            return customers;
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar clientes", e);
        }
    }

    @Override
    public List<Customer> searchActive(String query) {
        String sql = "SELECT * FROM customers WHERE active = 1 AND (" +
                "name LIKE ? OR phone LIKE ? OR address LIKE ?) ORDER BY name";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Customer> customers = new ArrayList<>();
                while (rs.next()) customers.add(mapRow(rs));
                return customers;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar clientes", e);
        }
    }

    @Override
    public void deactivateById(int id) {
        String sql = "UPDATE customers SET active = 0 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al desactivar cliente", e);
        }
    }

    @Override
    public double getPendingDebtByCustomerId(int customerId) {
        String sql = "SELECT COALESCE(SUM(x.pending), 0) AS pending_total " +
                "FROM ( " +
                "   SELECT (s.total - COALESCE(SUM(a.applied_amount), 0)) AS pending " +
                "   FROM sales s " +
                "   LEFT JOIN customer_payment_applications a ON a.sale_id = s.id " +
                "   WHERE s.customer_id = ? AND s.payment_method IN ('Cuenta corriente', 'CUENTA_CORRIENTE') " +
                "   GROUP BY s.id, s.total " +
                ") x " +
                "WHERE x.pending > 0.000001";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble("pending_total") : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar deuda pendiente del cliente", e);
        }
    }

    private Customer insert(Customer customer) {
        String sql = "INSERT INTO customers(name, phone, address, credit_limit, active, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, customer.getName());
            stmt.setString(2, customer.getPhone());
            stmt.setString(3, customer.getAddress());
            stmt.setDouble(4, customer.getCreditLimit());
            stmt.setInt(5, customer.isActive() ? 1 : 0);
            String createdAt = customer.getCreatedAt();
            if (createdAt == null || createdAt.isBlank()) {
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            stmt.setString(6, createdAt);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) customer.setId(keys.getInt(1));
            }
            customer.setCreatedAt(createdAt);
            return customer;
        } catch (SQLException e) {
            throw new RuntimeException("Error al insertar cliente", e);
        }
    }

    private Customer update(Customer customer) {
        String sql = "UPDATE customers SET name = ?, phone = ?, address = ?, credit_limit = ?, active = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customer.getName());
            stmt.setString(2, customer.getPhone());
            stmt.setString(3, customer.getAddress());
            stmt.setDouble(4, customer.getCreditLimit());
            stmt.setInt(5, customer.isActive() ? 1 : 0);
            stmt.setInt(6, customer.getId());
            stmt.executeUpdate();
            return customer;
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar cliente", e);
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setId(rs.getInt("id"));
        customer.setName(rs.getString("name"));
        customer.setPhone(rs.getString("phone"));
        customer.setAddress(rs.getString("address"));
        customer.setCreditLimit(rs.getDouble("credit_limit"));
        customer.setActive(rs.getInt("active") == 1);
        customer.setCreatedAt(rs.getString("created_at"));
        return customer;
    }
}
