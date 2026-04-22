package com.ferreteria.repositories.sqlite;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Expense;
import com.ferreteria.repositories.ExpenseRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteExpenseRepository implements ExpenseRepository {

    @Override
    public Expense save(Expense expense) {
        String sql = "INSERT INTO expenses(date, description, category, amount) VALUES (?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, expense.getDate());
            stmt.setString(2, expense.getDescription());
            stmt.setString(3, expense.getCategory());
            stmt.setDouble(4, expense.getAmount());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) expense.setId(keys.getInt(1));
            }
            return expense;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar gasto", e);
        }
    }

    @Override
    public void delete(int id) {
        String sql = "DELETE FROM expenses WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar gasto", e);
        }
    }

    @Override
    public List<Expense> findInRange(String dateTimeFrom, String dateTimeTo) {
        String sql = "SELECT id, date, description, category, amount FROM expenses " +
                "WHERE datetime(date) >= ? AND datetime(date) <= ? ORDER BY date DESC";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateTimeFrom);
            stmt.setString(2, dateTimeTo);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Expense> list = new ArrayList<>();
                while (rs.next()) {
                    Expense e = new Expense(
                            rs.getString("date"),
                            rs.getString("description"),
                            rs.getString("category"),
                            rs.getDouble("amount")
                    );
                    e.setId(rs.getInt("id"));
                    list.add(e);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener gastos en rango", e);
        }
    }

    @Override
    public double getTotalInRange(String dateTimeFrom, String dateTimeTo) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses " +
                "WHERE datetime(date) >= ? AND datetime(date) <= ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateTimeFrom);
            stmt.setString(2, dateTimeTo);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener total de gastos en rango", e);
        }
    }
}
