package com.ferreteria.repositories.sqlite;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Quote;
import com.ferreteria.models.QuoteLineItem;
import com.ferreteria.repositories.QuoteRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteQuoteRepository implements QuoteRepository {

    @Override
    public Quote save(Quote quote) {
        String sql = "INSERT INTO quotes(date, customer_id, customer_name, valid_days, notes, total) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, quote.getDate());
                if (quote.getCustomerId() == null) {
                    stmt.setNull(2, Types.INTEGER);
                } else {
                    stmt.setInt(2, quote.getCustomerId());
                }
                stmt.setString(3, quote.getCustomerName());
                stmt.setInt(4, quote.getValidDays());
                stmt.setString(5, quote.getNotes());
                stmt.setDouble(6, quote.getTotal());
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) quote.setId(keys.getInt(1));
                }
            }
            saveItems(conn, quote);
            conn.commit();
            return quote;
        } catch (SQLException e) {
            rollback(conn);
            throw new RuntimeException("Error al guardar presupuesto", e);
        } finally {
            restoreAutoCommit(conn);
        }
    }

    @Override
    public Quote update(Quote quote) {
        if (quote.getId() == null) {
            throw new IllegalArgumentException("El presupuesto no tiene id.");
        }
        String sql = "UPDATE quotes SET date = ?, customer_id = ?, customer_name = ?, valid_days = ?, notes = ?, total = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, quote.getDate());
                if (quote.getCustomerId() == null) {
                    stmt.setNull(2, Types.INTEGER);
                } else {
                    stmt.setInt(2, quote.getCustomerId());
                }
                stmt.setString(3, quote.getCustomerName());
                stmt.setInt(4, quote.getValidDays());
                stmt.setString(5, quote.getNotes());
                stmt.setDouble(6, quote.getTotal());
                stmt.setInt(7, quote.getId());
                stmt.executeUpdate();
            }
            // Las lineas se reemplazan completas: es mas simple que diferenciar altas/bajas.
            try (PreparedStatement clear = conn.prepareStatement("DELETE FROM quote_items WHERE quote_id = ?")) {
                clear.setInt(1, quote.getId());
                clear.executeUpdate();
            }
            saveItems(conn, quote);
            conn.commit();
            return quote;
        } catch (SQLException e) {
            rollback(conn);
            throw new RuntimeException("Error al actualizar presupuesto", e);
        } finally {
            restoreAutoCommit(conn);
        }
    }

    private void saveItems(Connection conn, Quote quote) throws SQLException {
        String sql = "INSERT INTO quote_items(quote_id, product_id, code, description, quantity, price, subtotal) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (QuoteLineItem item : quote.getItems()) {
                stmt.setInt(1, quote.getId());
                if (item.getProductId() == null) {
                    stmt.setNull(2, Types.INTEGER);
                } else {
                    stmt.setInt(2, item.getProductId());
                }
                stmt.setString(3, item.getCode());
                stmt.setString(4, item.getDescription());
                stmt.setDouble(5, item.getQuantity());
                stmt.setDouble(6, item.getPrice());
                stmt.setDouble(7, item.getSubtotal());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    @Override
    public void delete(int id) {
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement items = conn.prepareStatement("DELETE FROM quote_items WHERE quote_id = ?");
             PreparedStatement quote = conn.prepareStatement("DELETE FROM quotes WHERE id = ?")) {
            items.setInt(1, id);
            items.executeUpdate();
            quote.setInt(1, id);
            quote.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar presupuesto", e);
        }
    }

    @Override
    public List<Quote> findAll() {
        String sql = "SELECT q.id, q.date, q.customer_id, q.customer_name, q.valid_days, q.notes, q.total, " +
                "(SELECT COUNT(*) FROM quote_items i WHERE i.quote_id = q.id) AS item_count " +
                "FROM quotes q ORDER BY q.id DESC";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Quote> list = new ArrayList<>();
            while (rs.next()) {
                Quote quote = mapQuote(rs);
                quote.setItemCount(rs.getInt("item_count"));
                list.add(quote);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar presupuestos", e);
        }
    }

    @Override
    public Optional<Quote> findById(int id) {
        String sql = "SELECT id, date, customer_id, customer_name, valid_days, notes, total FROM quotes WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Quote quote = mapQuote(rs);
                quote.setItems(findItems(conn, id));
                return Optional.of(quote);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar presupuesto", e);
        }
    }

    private List<QuoteLineItem> findItems(Connection conn, int quoteId) throws SQLException {
        String sql = "SELECT product_id, code, description, quantity, price, subtotal FROM quote_items WHERE quote_id = ? ORDER BY id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, quoteId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<QuoteLineItem> items = new ArrayList<>();
                while (rs.next()) {
                    QuoteLineItem item = new QuoteLineItem();
                    int productId = rs.getInt("product_id");
                    item.setProductId(rs.wasNull() ? null : productId);
                    item.setCode(rs.getString("code"));
                    item.setDescription(rs.getString("description"));
                    item.setQuantity(rs.getDouble("quantity"));
                    item.setPrice(rs.getDouble("price"));
                    item.restoreSubtotal(rs.getDouble("subtotal"));
                    items.add(item);
                }
                return items;
            }
        }
    }

    private Quote mapQuote(ResultSet rs) throws SQLException {
        Quote quote = new Quote();
        quote.setId(rs.getInt("id"));
        quote.setDate(rs.getString("date"));
        int customerId = rs.getInt("customer_id");
        quote.setCustomerId(rs.wasNull() ? null : customerId);
        quote.setCustomerName(rs.getString("customer_name"));
        quote.setValidDays(rs.getInt("valid_days"));
        quote.setNotes(rs.getString("notes"));
        quote.setTotal(rs.getDouble("total"));
        return quote;
    }

    private void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void restoreAutoCommit(Connection conn) {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }
}
