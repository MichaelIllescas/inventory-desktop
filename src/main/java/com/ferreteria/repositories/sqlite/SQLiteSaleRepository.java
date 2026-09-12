package com.ferreteria.repositories.sqlite;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.CustomerCurrentAccountReportRow;
import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.Sale;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.repositories.SaleRepository;
import com.ferreteria.util.AppLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteSaleRepository implements SaleRepository {
    private static final String PAYMENT_CURRENT_ACCOUNT = "Cuenta corriente";
    private static final String PAYMENT_CURRENT_ACCOUNT_LEGACY = "CUENTA_CORRIENTE";

    @Override
    public Sale saveSale(Sale sale) {
        String sql = "INSERT INTO sales(date, total, payment_method, customer_id) VALUES (?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, sale.getDate());
            stmt.setDouble(2, sale.getTotal());
            stmt.setString(3, sale.getPaymentMethod());
            if (sale.getCustomerId() == null) {
                stmt.setNull(4, Types.INTEGER);
            } else {
                stmt.setInt(4, sale.getCustomerId());
            }
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    sale.setId(keys.getInt(1));
                }
            }
            AppLogger.info("SQLiteSaleRepository", "saveSale", "Venta insertada en BD: id=" + sale.getId());
            return sale;
        } catch (SQLException e) {
            AppLogger.error("SQLiteSaleRepository", "saveSale", "Error SQL al guardar venta: " + e.getMessage(), e);
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
            AppLogger.info("SQLiteSaleRepository", "saveSaleItems",
                    "Ítems guardados: saleId=" + saleId + " cantidad=" + items.size());
        } catch (SQLException e) {
            AppLogger.error("SQLiteSaleRepository", "saveSaleItems",
                    "Error SQL al guardar ítems: saleId=" + saleId + " items=" + items.size(), e);
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
        String sql = "SELECT COALESCE(SUM(s.total), 0) " +
                "FROM sales s " +
                "WHERE datetime(s.date) >= ? AND datetime(s.date) <= ? " +
                "AND EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id)";
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
    public double getCollectedTotalInRange(String dateFrom, String dateTo) {
        String cashSalesSql = "SELECT COALESCE(SUM(s.total), 0) FROM sales s " +
                "WHERE datetime(s.date) >= ? AND datetime(s.date) <= ? " +
                "AND (s.payment_method IS NULL OR s.payment_method NOT IN (?, ?)) " +
                "AND EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id)";
        String currentAccountPaymentsSql = "SELECT COALESCE(SUM(m.amount), 0) " +
                "FROM customer_account_movements m " +
                "WHERE m.type = 'CREDITO' " +
                "AND datetime(replace(substr(m.date,1,19),'T',' ')) >= ? " +
                "AND datetime(replace(substr(m.date,1,19),'T',' ')) <= ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement cashStmt = conn.prepareStatement(cashSalesSql);
             PreparedStatement paymentsStmt = conn.prepareStatement(currentAccountPaymentsSql)) {
            cashStmt.setString(1, dateFrom);
            cashStmt.setString(2, dateTo);
            cashStmt.setString(3, PAYMENT_CURRENT_ACCOUNT);
            cashStmt.setString(4, PAYMENT_CURRENT_ACCOUNT_LEGACY);
            double cashSales;
            try (ResultSet rs = cashStmt.executeQuery()) {
                cashSales = rs.next() ? rs.getDouble(1) : 0;
            }

            paymentsStmt.setString(1, dateFrom);
            paymentsStmt.setString(2, dateTo);
            double currentAccountPayments;
            try (ResultSet rs = paymentsStmt.executeQuery()) {
                currentAccountPayments = rs.next() ? rs.getDouble(1) : 0;
            }
            return cashSales + currentAccountPayments;
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener total cobrado en rango", e);
        }
    }

    @Override
    public double getCurrentAccountPaymentsTotalInRange(String dateFrom, String dateTo) {
        String sql = "SELECT COALESCE(SUM(m.amount), 0) " +
                "FROM customer_account_movements m " +
                "WHERE m.type = 'CREDITO' " +
                "AND datetime(replace(substr(m.date,1,19),'T',' ')) >= ? " +
                "AND datetime(replace(substr(m.date,1,19),'T',' ')) <= ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener pagos de cuenta corriente en rango", e);
        }
    }

    @Override
    public double getCurrentAccountSalesTotalInRange(String dateFrom, String dateTo) {
        String sql = "SELECT COALESCE(SUM(s.total), 0) FROM sales s " +
                "WHERE datetime(s.date) >= ? AND datetime(s.date) <= ? " +
                "AND s.payment_method IN (?, ?) " +
                "AND EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            stmt.setString(3, PAYMENT_CURRENT_ACCOUNT);
            stmt.setString(4, PAYMENT_CURRENT_ACCOUNT_LEGACY);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ventas de cuenta corriente en rango", e);
        }
    }

    @Override
    public List<SalesByDay> getSalesByDayInRange(String dateFrom, String dateTo) {
        String sql = "SELECT date(s.date) AS day," +
                " SUM(s.total) + COALESCE(p.cash, 0) + COALESCE(p.transfer, 0) + COALESCE(p.debit, 0) + COALESCE(p.credit, 0) AS total," +
                " SUM(CASE WHEN s.payment_method = 'Efectivo' THEN s.total ELSE 0 END) + COALESCE(p.cash, 0) AS cash," +
                " SUM(CASE WHEN s.payment_method = 'Transferencia' THEN s.total ELSE 0 END) + COALESCE(p.transfer, 0) AS transfer," +
                " SUM(CASE WHEN s.payment_method = 'Debito' OR s.payment_method = 'Débito' THEN s.total ELSE 0 END) + COALESCE(p.debit, 0) AS debit," +
                " SUM(CASE WHEN s.payment_method = 'Credito' OR s.payment_method = 'Crédito' THEN s.total ELSE 0 END) + COALESCE(p.credit, 0) AS credit," +
                " SUM(CASE WHEN s.payment_method IN (?, ?) THEN s.total ELSE 0 END) AS current_account" +
                " FROM sales s" +
                " LEFT JOIN (" +
                "   SELECT date(replace(substr(cp.date,1,19),'T',' ')) AS pay_day," +
                "     SUM(CASE WHEN cp.payment_method = 'Efectivo' THEN cp.amount ELSE 0 END) AS cash," +
                "     SUM(CASE WHEN cp.payment_method = 'Transferencia' THEN cp.amount ELSE 0 END) AS transfer," +
                "     SUM(CASE WHEN cp.payment_method = 'Debito' OR cp.payment_method = 'Débito' THEN cp.amount ELSE 0 END) AS debit," +
                "     SUM(CASE WHEN cp.payment_method = 'Credito' OR cp.payment_method = 'Crédito' THEN cp.amount ELSE 0 END) AS credit" +
                "   FROM customer_payments cp" +
                "   WHERE datetime(replace(substr(cp.date,1,19),'T',' ')) >= ?" +
                "     AND datetime(replace(substr(cp.date,1,19),'T',' ')) <= ?" +
                "   GROUP BY pay_day" +
                " ) p ON date(s.date) = p.pay_day" +
                " WHERE datetime(s.date) >= ? AND datetime(s.date) <= ?" +
                " AND EXISTS (SELECT 1 FROM sale_items si WHERE si.sale_id = s.id)" +
                " GROUP BY date(s.date) ORDER BY day";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, PAYMENT_CURRENT_ACCOUNT);
            stmt.setString(2, PAYMENT_CURRENT_ACCOUNT_LEGACY);
            stmt.setString(3, dateFrom);
            stmt.setString(4, dateTo);
            stmt.setString(5, dateFrom);
            stmt.setString(6, dateTo);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SalesByDay> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new SalesByDay(
                            rs.getString("day"),
                            rs.getDouble("total"),
                            rs.getDouble("cash"),
                            rs.getDouble("transfer"),
                            rs.getDouble("debit"),
                            rs.getDouble("credit"),
                            rs.getDouble("current_account")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ventas por dia en rango", e);
        }
    }

    @Override
    public List<ProductSalesReport> getTopProductsInRange(String dateFrom, String dateTo, int limit) {
        String sql = "SELECT p.code, p.name, SUM(si.quantity) AS qty, SUM(si.quantity * si.price) AS revenue " +
                "FROM sale_items si JOIN products p ON si.product_id = p.id " +
                "JOIN sales s ON si.sale_id = s.id WHERE datetime(s.date) >= ? AND datetime(s.date) <= ? " +
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
        String sql = SALE_DETAIL_SELECT +
                "WHERE datetime(s.date) >= ? AND datetime(s.date) <= ? ORDER BY s.date, s.id, si.id";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dateFrom);
            stmt.setString(2, dateTo);
            return readSaleDetails(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener detalle de ventas", e);
        }
    }

    @Override
    public List<SaleDetailRow> getSaleDetailsBySaleId(int saleId) {
        String sql = SALE_DETAIL_SELECT + "WHERE s.id = ? ORDER BY si.id";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            return readSaleDetails(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener el detalle de la venta " + saleId, e);
        }
    }

    private static final String SALE_DETAIL_SELECT =
            "SELECT s.id AS sale_id, s.date AS sale_date, p.id AS product_id, p.code, p.name, si.quantity, si.price, " +
            "(si.quantity * si.price) AS subtotal, s.total AS sale_total, s.payment_method, " +
            "c.name AS customer_name " +
            "FROM sales s JOIN sale_items si ON s.id = si.sale_id JOIN products p ON si.product_id = p.id " +
            "LEFT JOIN customers c ON c.id = s.customer_id ";

    private static List<SaleDetailRow> readSaleDetails(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<SaleDetailRow> list = new ArrayList<>();
            while (rs.next()) {
                String code = rs.getString("code");
                if (code == null) code = "";
                SaleDetailRow row = new SaleDetailRow(
                        rs.getInt("sale_id"),
                        rs.getInt("product_id"),
                        rs.getString("sale_date"),
                        code,
                        rs.getString("name"),
                        rs.getDouble("quantity"),
                        rs.getDouble("price"),
                        rs.getDouble("subtotal"),
                        rs.getDouble("sale_total"),
                        rs.getString("payment_method")
                );
                row.setCustomerName(rs.getString("customer_name"));
                list.add(row);
            }
            return list;
        }
    }

    @Override
    public List<CustomerCurrentAccountReportRow> getCurrentAccountReportByCustomer(String dateFrom, String dateTo) {
        String normMovementDate = "replace(substr(m.date,1,19),'T',' ')";
        String normSaleDate = "replace(substr(s.date,1,19),'T',' ')";
        String sql = "WITH " +
                "initial_balance AS ( " +
                "  SELECT m.customer_id AS customer_id, " +
                "         COALESCE(SUM(CASE WHEN m.type = 'DEBITO' THEN m.amount WHEN m.type = 'CREDITO' THEN -m.amount ELSE 0 END),0) AS total " +
                "  FROM customer_account_movements m " +
                "  WHERE " + normMovementDate + " < ? " +
                "  GROUP BY m.customer_id " +
                "), " +
                "manual_initial_period AS ( " +
                "  SELECT m.customer_id AS customer_id, COALESCE(SUM(m.amount),0) AS total " +
                "  FROM customer_account_movements m " +
                "  WHERE m.type = 'DEBITO' " +
                "    AND m.sale_id IS NULL " +
                "    AND m.payment_id IS NULL " +
                "    AND " + normMovementDate + " >= ? " +
                "    AND " + normMovementDate + " <= ? " +
                "  GROUP BY m.customer_id " +
                "), " +
                "sales_period AS ( " +
                "  SELECT s.customer_id AS customer_id, COALESCE(SUM(s.total),0) AS total " +
                "  FROM sales s " +
                "  WHERE s.customer_id IS NOT NULL AND s.payment_method IN (?, ?) AND " + normSaleDate + " >= ? AND " + normSaleDate + " <= ? " +
                "  GROUP BY s.customer_id " +
                "), " +
                "payments_period AS ( " +
                "  SELECT m.customer_id AS customer_id, COALESCE(SUM(m.amount),0) AS total " +
                "  FROM customer_account_movements m " +
                "  WHERE m.type = 'CREDITO' AND " + normMovementDate + " >= ? AND " + normMovementDate + " <= ? " +
                "  GROUP BY m.customer_id " +
                "), " +
                "final_balance AS ( " +
                "  SELECT m.customer_id AS customer_id, " +
                "         COALESCE(SUM(CASE WHEN m.type = 'DEBITO' THEN m.amount WHEN m.type = 'CREDITO' THEN -m.amount ELSE 0 END),0) AS total " +
                "  FROM customer_account_movements m " +
                "  WHERE " + normMovementDate + " <= ? " +
                "  GROUP BY m.customer_id " +
                ") " +
                "SELECT c.id AS customer_id, c.name AS customer_name, " +
                "       (COALESCE(ib.total,0) + COALESCE(mip.total,0)) AS initial_debt, " +
                "       COALESCE(sp.total,0) AS period_sales, " +
                "       COALESCE(pp.total,0) AS period_payments, " +
                "       COALESCE(fb.total,0) AS final_debt " +
                "FROM customers c " +
                "LEFT JOIN initial_balance ib ON ib.customer_id = c.id " +
                "LEFT JOIN manual_initial_period mip ON mip.customer_id = c.id " +
                "LEFT JOIN sales_period sp ON sp.customer_id = c.id " +
                "LEFT JOIN payments_period pp ON pp.customer_id = c.id " +
                "LEFT JOIN final_balance fb ON fb.customer_id = c.id " +
                "WHERE c.active = 1 " +
                "  AND (ABS(COALESCE(ib.total,0) + COALESCE(mip.total,0)) > 0.000001 " +
                "       OR ABS(COALESCE(sp.total,0)) > 0.000001 " +
                "       OR ABS(COALESCE(pp.total,0)) > 0.000001 " +
                "       OR ABS(COALESCE(fb.total,0)) > 0.000001) " +
                "ORDER BY final_debt DESC, c.name";

        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setString(i++, dateFrom);
            stmt.setString(i++, dateFrom);
            stmt.setString(i++, dateTo);
            stmt.setString(i++, PAYMENT_CURRENT_ACCOUNT);
            stmt.setString(i++, PAYMENT_CURRENT_ACCOUNT_LEGACY);
            stmt.setString(i++, dateFrom);
            stmt.setString(i++, dateTo);
            stmt.setString(i++, dateFrom);
            stmt.setString(i++, dateTo);
            stmt.setString(i++, dateTo);
            try (ResultSet rs = stmt.executeQuery()) {
                List<CustomerCurrentAccountReportRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CustomerCurrentAccountReportRow(
                            rs.getInt("customer_id"),
                            rs.getString("customer_name"),
                            rs.getDouble("initial_debt"),
                            rs.getDouble("period_sales"),
                            rs.getDouble("period_payments"),
                            rs.getDouble("final_debt")
                    ));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener reporte de cuenta corriente por cliente", e);
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

