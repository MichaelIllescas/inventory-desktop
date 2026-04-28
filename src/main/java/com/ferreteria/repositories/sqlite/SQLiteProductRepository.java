package com.ferreteria.repositories.sqlite;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.Product;
import com.ferreteria.models.ReplenishmentItem;
import com.ferreteria.repositories.ProductRepository;
import com.ferreteria.util.AppLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteProductRepository implements ProductRepository {

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            return insert(product);
        }
        return update(product);
    }

    private Product insert(Product product) {
        String sql = "INSERT INTO products(code, name, description, price, stock, minimum_stock, supplier_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, product.getCode());
            stmt.setString(2, product.getName());
            stmt.setString(3, product.getDescription());
            stmt.setDouble(4, product.getPrice());
            stmt.setDouble(5, product.getStock());
            stmt.setDouble(6, product.getMinimumStock());

            if (product.getSupplierId() != null) {
                stmt.setInt(7, product.getSupplierId());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    product.setId(keys.getInt(1));
                }
            }
            return product;
        } catch (SQLException e) {
            throw new RuntimeException("Error al insertar producto", e);
        }
    }

    private Product update(Product product) {
        String sql = "UPDATE products SET code = ?, name = ?, description = ?, price = ?, stock = ?, minimum_stock = ?, supplier_id = ?, precarga = ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, product.getCode());
            stmt.setString(2, product.getName());
            stmt.setString(3, product.getDescription());
            stmt.setDouble(4, product.getPrice());
            stmt.setDouble(5, product.getStock());
            stmt.setDouble(6, product.getMinimumStock());

            if (product.getSupplierId() != null) {
                stmt.setInt(7, product.getSupplierId());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }

            stmt.setInt(8, product.isPrecarga() ? 1 : 0);
            stmt.setInt(9, product.getId());
            stmt.executeUpdate();
            return product;
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar producto", e);
        }
    }

    @Override
    public void deleteById(int id) {
        String sql = "UPDATE products SET deleted = 1 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar producto", e);
        }
    }

    @Override
    public Optional<Product> findById(int id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar producto", e);
        }
    }

    @Override
    public List<Product> findAll() {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id WHERE p.precarga = 0 AND p.deleted = 0 ORDER BY p.name";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                products.add(mapRowWithSupplier(rs));
            }
            return products;
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar productos", e);
        }
    }

    @Override
    public List<Product> search(String query) {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id WHERE (p.code LIKE ? OR p.name LIKE ? OR p.description LIKE ?) AND p.precarga = 0 AND p.deleted = 0 ORDER BY p.name";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) {
                    products.add(mapRowWithSupplier(rs));
                }
                return products;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar productos", e);
        }
    }

    @Override
    public Optional<Product> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String trimmed = code.trim();
        String sql = "SELECT p.*, s.name AS supplier_name FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id WHERE (p.code = ? OR p.code LIKE ?) AND p.deleted = 0 ORDER BY CASE WHEN p.code = ? THEN 0 ELSE 1 END, p.code LIMIT 1";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trimmed);
            stmt.setString(2, trimmed + "%");
            stmt.setString(3, trimmed);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowWithSupplier(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar producto por código", e);
        }
        // No está en inventory.db — buscar en la BD externa de precarga
        return findByCodeInExternal(trimmed);
    }

    private Optional<Product> findByCodeInExternal(String code) {
        String dbPath = DatabaseManager.getExternalProductsDbPath();
        if (dbPath == null) return Optional.empty();
        String url = "jdbc:sqlite:" + dbPath.replace("\\", "/");
        try (java.sql.Connection extConn = java.sql.DriverManager.getConnection(url);
             PreparedStatement stmt = extConn.prepareStatement(
                     "SELECT ean, producto, brand FROM productos WHERE ean = ? LIMIT 1")) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Product p = new Product();
                    p.setCode(rs.getString("ean"));
                    p.setName(rs.getString("producto") != null ? rs.getString("producto") : "");
                    p.setDescription(rs.getString("brand") != null ? rs.getString("brand") : "");
                    p.setPrecarga(true);
                    return Optional.of(p);
                }
            }
        } catch (SQLException e) {
            AppLogger.warn("SQLiteProductRepository", "findByCodeInExternal",
                    "No se pudo buscar en BD externa: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void decreaseStock(int productId, double quantity) {
        String sql = "UPDATE products SET stock = stock - ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, quantity);
            stmt.setInt(2, productId);
            stmt.executeUpdate();
            AppLogger.info("SQLiteProductRepository", "decreaseStock",
                    "Stock descontado: productoId=" + productId + " cantidad=" + quantity);
        } catch (SQLException e) {
            AppLogger.error("SQLiteProductRepository", "decreaseStock",
                    "Error SQL al descontar stock: productoId=" + productId, e);
            throw new RuntimeException("Error al descontar stock", e);
        }
    }

    @Override
    public void increaseStock(int productId, double quantity) {
        String sql = "UPDATE products SET stock = stock + ? WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, quantity);
            stmt.setInt(2, productId);
            stmt.executeUpdate();
            AppLogger.info("SQLiteProductRepository", "increaseStock",
                    "Stock devuelto: productoId=" + productId + " cantidad=" + quantity);
        } catch (SQLException e) {
            AppLogger.error("SQLiteProductRepository", "increaseStock",
                    "Error SQL al devolver stock: productoId=" + productId, e);
            throw new RuntimeException("Error al devolver stock", e);
        }
    }

    @Override
    public List<Product> findLowStock() {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id WHERE p.stock <= p.minimum_stock AND p.precarga = 0 AND p.deleted = 0 ORDER BY p.stock ASC";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                products.add(mapRowWithSupplier(rs));
            }
            return products;
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar productos con bajo stock", e);
        }
    }

    @Override
    public List<ReplenishmentItem> findReplenishmentList() {
        String sql = "SELECT p.code AS product_code, p.name AS product_name, p.stock, p.minimum_stock, s.name AS supplier_name, s.phone AS supplier_phone, s.email AS supplier_email, s.address AS supplier_address FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id WHERE p.stock <= p.minimum_stock AND p.precarga = 0 AND p.deleted = 0 ORDER BY p.stock ASC";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<ReplenishmentItem> list = new ArrayList<>();
            while (rs.next()) {
                ReplenishmentItem item = new ReplenishmentItem();
                item.setProductCode(rs.getString("product_code"));
                item.setProductName(rs.getString("product_name"));
                item.setStock(rs.getDouble("stock"));
                item.setMinimumStock(rs.getDouble("minimum_stock"));
                item.setSupplierName(rs.getString("supplier_name"));
                item.setSupplierPhone(rs.getString("supplier_phone"));
                item.setSupplierEmail(rs.getString("supplier_email"));
                item.setSupplierAddress(rs.getString("supplier_address"));
                list.add(item);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar reposición", e);
        }
    }

    @Override
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM products WHERE code != ? AND (skip_stock = 0 OR skip_stock IS NULL) AND precarga = 0 AND deleted = 0";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, DatabaseManager.VARIOS_CODE);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar productos", e);
        }
    }

    @Override
    public int countLowStock() {
        String sql = "SELECT COUNT(*) FROM products WHERE stock <= minimum_stock AND code != ? AND (skip_stock = 0 OR skip_stock IS NULL) AND precarga = 0 AND deleted = 0";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, DatabaseManager.VARIOS_CODE);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar bajo stock", e);
        }
    }

    @Override
    public int countSearch(String query) {
        String sql = "SELECT COUNT(*) FROM products WHERE (code LIKE ? OR name LIKE ? OR description LIKE ?) AND code != ? AND (skip_stock = 0 OR skip_stock IS NULL) AND precarga = 0 AND deleted = 0";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, DatabaseManager.VARIOS_CODE);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar búsqueda", e);
        }
    }

    @Override
    public List<Product> findPage(int offset, int pageSize) {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name " +
                "FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "WHERE p.code != ? AND (p.skip_stock = 0 OR p.skip_stock IS NULL) AND p.precarga = 0 AND p.deleted = 0 " +
                "ORDER BY p.name LIMIT ? OFFSET ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, DatabaseManager.VARIOS_CODE);
            stmt.setInt(2, pageSize);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) products.add(mapRowWithSupplier(rs));
                return products;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al paginar productos", e);
        }
    }

    @Override
    public List<Product> searchPage(String query, int offset, int pageSize) {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name " +
                "FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "WHERE (p.code LIKE ? OR p.name LIKE ? OR p.description LIKE ?) AND p.code != ? AND (p.skip_stock = 0 OR p.skip_stock IS NULL) AND p.precarga = 0 AND p.deleted = 0 " +
                "ORDER BY p.name LIMIT ? OFFSET ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, DatabaseManager.VARIOS_CODE);
            stmt.setInt(5, pageSize);
            stmt.setInt(6, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) products.add(mapRowWithSupplier(rs));
                return products;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al paginar búsqueda", e);
        }
    }

    private Product mapRowWithSupplier(ResultSet rs) throws SQLException {
        Product product = mapRow(rs);
        String supplierName = rs.getString("supplier_name");
        product.setSupplierName(supplierName != null ? supplierName : "");
        return product;
    }

    public List<Product> findAllIncludingPrecarga() {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name " +
                "FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "WHERE p.code != ? AND p.skip_stock = 0 AND p.deleted = 0 " +
                "ORDER BY p.precarga ASC, p.name ASC LIMIT 500";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, DatabaseManager.VARIOS_CODE);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Product> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowWithSupplier(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar productos con precarga", e);
        }
    }

    public List<Product> searchIncludingPrecarga(String query) {
        String sql = "SELECT p.id, p.code, p.name, p.description, p.price, p.stock, p.minimum_stock, p.supplier_id, p.skip_stock, p.precarga, s.name AS supplier_name " +
                "FROM products p LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "WHERE (p.code LIKE ? OR p.name LIKE ? OR p.description LIKE ?) AND p.code != ? AND p.skip_stock = 0 AND p.deleted = 0 " +
                "ORDER BY p.precarga ASC, p.name ASC LIMIT 200";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, DatabaseManager.VARIOS_CODE);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Product> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowWithSupplier(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar productos con precarga", e);
        }
    }

    public void activarProducto(int productId, double price, double stock, double minimumStock) {
        String sql = "UPDATE products SET price = ?, stock = ?, minimum_stock = ?, precarga = 0 WHERE id = ?";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setDouble(2, stock);
            stmt.setDouble(3, minimumStock);
            stmt.setInt(4, productId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al activar producto", e);
        }
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setId(rs.getInt("id"));
        product.setCode(rs.getString("code"));
        product.setName(rs.getString("name"));
        product.setDescription(rs.getString("description"));
        product.setPrice(rs.getDouble("price"));
        product.setStock(rs.getDouble("stock"));
        product.setMinimumStock(rs.getDouble("minimum_stock"));
        try { product.setSkipStock(rs.getInt("skip_stock") == 1); } catch (SQLException ignored) {}
        try { product.setPrecarga(rs.getInt("precarga") == 1); } catch (SQLException ignored) {}

        int supplierId = rs.getInt("supplier_id");
        if (rs.wasNull()) {
            product.setSupplierId(null);
        } else {
            product.setSupplierId(supplierId);
        }
        return product;
    }
}

