package com.ferreteria.repositories.sqlite;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.Sale;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.repositories.SaleRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteSaleRepository implements SaleRepository {

    @Override
    public Sale saveSale(Sale sale) {
        String sql = "INSERT INTO sales(date, total, payment_method) VALUES (?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, sale.getDate());
            stmt.setDouble(2, sale.getTotal());
            stmt.setString(3, sale.getPaymentMethod());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    sale.setId(keys.getInt(1));
                }
            }
            return sale;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar venta", e);
        }
    }

    @Override
    public void saveSaleItems(int saleId, List<SaleItem> items) {
        String sql = "INSERT INTO sale_items(sale_id, product_id, quantity, price) VALUES (?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (SaleItem item : items) {
                stmt.setInt(1, saleId);
                stmt.setInt(2, item.getProductId());
                stmt.setDouble(3, item.getQuantity());
                stmt.setDouble(4, item.getPrice());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar ítems de venta", e);
        }
    }

    @Override
    public double getTodaySalesTotal() {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM sales WHERE date(date) = date('now', 'localtime')";
        return querySum(sql);
    }

    @Override
    public double getMonthSalesTotal() {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM sales WHERE strftime('%Y-%m', date) = strftime('%Y-%m', 'now', 'localtime')";
        return querySum(sql);
    }

    @Override
    public List<SalesByDay> getSalesLastDays(int days) {
        String sql = "SELECT date(date) AS day, SUM(total) AS total FROM sales WHERE date(date) >= date('now', 'localtime', '-' || ? || ' days') GROUP BY date(date) ORDER BY day";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, days);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SalesByDay> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new SalesByDay(rs.getString("day"), rs.getDouble("total")));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ventas por día", e);
        }
    }

    private double querySum(String sql) {
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar total de ventas", e);
        }
    }

    @Override
    public double getSalesTotalInRange(String dateFrom, String dateTo) {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM sales WHERE date(date) >= ? AND date(date) <= ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener total en rango", e);
        }
    }

    @Override
    public List<SalesByDay> getSalesByDayInRange(String dateFrom, String dateTo) {
        String sql = "SELECT date(date) AS day," +
                " SUM(total) AS total," +
                " SUM(CASE WHEN payment_method = 'Efectivo' THEN total ELSE 0 END) AS cash," +
                " SUM(CASE WHEN payment_method = 'Transferencia' THEN total ELSE 0 END) AS transfer," +
                " SUM(CASE WHEN payment_method = 'Débito' THEN total ELSE 0 END) AS debit," +
                " SUM(CASE WHEN payment_method = 'Crédito' THEN total ELSE 0 END) AS credit" +
                " FROM sales WHERE date(date) >= ? AND date(date) <= ? GROUP BY date(date) ORDER BY day";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SalesByDay> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new SalesByDay(
                            rs.getString("day"),
                            rs.getDouble("total"),
                            rs.getDouble("cash"),
                            rs.getDouble("transfer"),
                            rs.getDouble("debit"),
                            rs.getDouble("credit")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ventas por día en rango", e);
        }
    }

    @Override
    public List<ProductSalesReport> getTopProductsInRange(String dateFrom, String dateTo, int limit) {
        String sql = "SELECT p.code, p.name, SUM(si.quantity) AS qty, SUM(si.quantity * si.price) AS revenue " +
                "FROM sale_items si JOIN products p ON si.product_id = p.id " +
                "JOIN sales s ON si.sale_id = s.id WHERE date(s.date) >= ? AND date(s.date) <= ? " +
                "GROUP BY si.product_id ORDER BY qty DESC LIMIT ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                List<ProductSalesReport> list = new ArrayList<>();
                while (rs.next()) {
                    String code = rs.getString("code");
                    if (code == null) code = "";
                    list.add(new ProductSalesReport(
                            code,
                            rs.getString("name"),
                            rs.getDouble("qty"),
                            rs.getDouble("revenue")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener productos más vendidos", e);
        }
    }

    @Override
    public List<SaleDetailRow> getSaleDetailsInRange(String dateFrom, String dateTo) {
        String sql = "SELECT s.id AS sale_id, date(s.date) AS sale_date, p.code, p.name, si.quantity, si.price, (si.quantity * si.price) AS subtotal, s.payment_method " +
                "FROM sales s JOIN sale_items si ON s.id = si.sale_id JOIN products p ON si.product_id = p.id " +
                "WHERE date(s.date) >= ? AND date(s.date) <= ? ORDER BY s.date, s.id, si.id";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SaleDetailRow> list = new ArrayList<>();
                while (rs.next()) {
                    String code = rs.getString("code");
                    if (code == null) code = "";
                    list.add(new SaleDetailRow(
                            rs.getInt("sale_id"),
                            rs.getString("sale_date"),
                            code,
                            rs.getString("name"),
                            rs.getDouble("quantity"),
                            rs.getDouble("price"),
                            rs.getDouble("subtotal"),
                            rs.getString("payment_method")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener detalle de ventas", e);
        }
    }

    @Override
    public List<SaleItem> getSaleItemsBySaleId(int saleId) {
        String sql = "SELECT id, sale_id, product_id, quantity, price FROM sale_items WHERE sale_id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SaleItem> list = new ArrayList<>();
                while (rs.next()) {
                    SaleItem item = new SaleItem();
                    item.setId(rs.getInt("id"));
                    item.setSaleId(rs.getInt("sale_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setQuantity(rs.getDouble("quantity"));
                    item.setPrice(rs.getDouble("price"));
                    list.add(item);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ítems de la venta", e);
        }
    }

    @Override
    public void deleteSale(int saleId) {
        String sql = "DELETE FROM sales WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar la venta", e);
        }
    }
}
