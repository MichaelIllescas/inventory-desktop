package com.ferreteria.database;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public final class DatabaseManager {

    private static final String DB_FILE = "inventory.db";
    private static final String DB_FOLDER = getDatabaseFolder();

    private static String getDatabaseFolder() {
        // Solo usar carpeta "data" del proyecto cuando se pasa -Dsistema.inventario.dev=true (desarrollo).
        // La app instalada NUNCA recibe esa propiedad, así que siempre usa AppData = BD nueva por usuario.
        boolean dev = "true".equalsIgnoreCase(System.getProperty("sistema.inventario.dev", ""));
        if (dev) {
            String userDir = System.getProperty("user.dir", "");
            Path relativeData = Paths.get(userDir, "data");
            if (Files.exists(relativeData.getParent()) && isWritable(relativeData)) {
                return relativeData.toString();
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                base = Paths.get(localAppData, "Sistema de Inventario");
            } else {
                base = Paths.get(System.getProperty("user.home"), "AppData", "Local", "Sistema de Inventario");
            }
        } else {
            base = Paths.get(System.getProperty("user.home"), ".sistema-inventario");
        }
        return base.resolve("data").toString();
    }

    private static boolean isWritable(Path folder) {
        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }
            Path test = folder.resolve(".write_test_" + System.currentTimeMillis());
            Files.write(test, new byte[0]);
            Files.delete(test);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getDbUrl() {
        return "jdbc:sqlite:" + DB_FOLDER.replace("\\", "/") + "/" + DB_FILE;
    }

    private static Connection connection;
    private static boolean schemaInitialized;

    private DatabaseManager() {
    }

    public static synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                ensureDatabaseFolder();
                connection = DriverManager.getConnection(getDbUrl());
                enableForeignKeys(connection);
                if (!schemaInitialized) {
                    initSchema(connection);
                    migrateToDecimalQuantities(connection);
                    migrateSalesPaymentMethod(connection);
                    schemaInitialized = true;
                }
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Error al conectar con la base de datos", e);
        }
    }

    private static void initSchema(Connection conn) {
        String sql;
        try (var is = DatabaseManager.class.getResourceAsStream("/database/schema.sql");
             var reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             var br = new BufferedReader(reader)) {
            sql = br.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar el esquema de la base de datos", e);
        }
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(trimmed);
                } catch (SQLException e) {
                    throw new RuntimeException("Error ejecutando esquema: " + trimmed, e);
                }
            }
        }
    }

    /** Migra tablas existentes de INTEGER a REAL para quantity y stock (cantidades decimales). */
    private static void migrateToDecimalQuantities(Connection conn) {
        try {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = OFF");
            }
            try {
                boolean needMigrateSaleItems = false;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("PRAGMA table_info(sale_items)")) {
                    while (rs.next()) {
                        if ("quantity".equalsIgnoreCase(rs.getString("name")) && "INTEGER".equalsIgnoreCase(rs.getString("type"))) {
                            needMigrateSaleItems = true;
                            break;
                        }
                    }
                }
                if (needMigrateSaleItems) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("DROP TABLE IF EXISTS sale_items_new");
                        st.execute("CREATE TABLE sale_items_new (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity REAL NOT NULL, price REAL NOT NULL, FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE, FOREIGN KEY (product_id) REFERENCES products(id))");
                        st.execute("INSERT INTO sale_items_new (id, sale_id, product_id, quantity, price) SELECT id, sale_id, product_id, CAST(quantity AS REAL), price FROM sale_items");
                        st.execute("DROP TABLE sale_items");
                        st.execute("ALTER TABLE sale_items_new RENAME TO sale_items");
                    }
                }
                boolean needMigrateProducts = false;
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("PRAGMA table_info(products)")) {
                    while (rs.next()) {
                        String col = rs.getString("name");
                        if (("stock".equalsIgnoreCase(col) || "minimum_stock".equalsIgnoreCase(col)) && "INTEGER".equalsIgnoreCase(rs.getString("type"))) {
                            needMigrateProducts = true;
                            break;
                        }
                    }
                }
                if (needMigrateProducts) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("DROP TABLE IF EXISTS products_new");
                        st.execute("CREATE TABLE products_new (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, name TEXT NOT NULL, description TEXT, price REAL NOT NULL, stock REAL NOT NULL DEFAULT 0, minimum_stock REAL NOT NULL DEFAULT 0, supplier_id INTEGER, FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL)");
                        st.execute("INSERT INTO products_new (id, code, name, description, price, stock, minimum_stock, supplier_id) SELECT id, code, name, description, price, CAST(stock AS REAL), CAST(minimum_stock AS REAL), supplier_id FROM products");
                        st.execute("DROP TABLE products");
                        st.execute("ALTER TABLE products_new RENAME TO products");
                    }
                }
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en migración a cantidades decimales", e);
        }
    }

    /** Añade columna payment_method a sales si aún no existe (medios de pago). */
    private static void migrateSalesPaymentMethod(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(sales)")) {
            boolean hasColumn = false;
            while (rs.next()) {
                if ("payment_method".equalsIgnoreCase(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
            if (!hasColumn) {
                try (Statement alter = conn.createStatement()) {
                    alter.execute("ALTER TABLE sales ADD COLUMN payment_method TEXT");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al migrar columna payment_method en sales", e);
        }
    }

    private static void ensureDatabaseFolder() {
        try {
            Path folder = Paths.get(DB_FOLDER);
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear la carpeta de base de datos: " + DB_FOLDER, e);
        }
    }

    private static void enableForeignKeys(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo activar foreign_keys en SQLite", e);
        }
    }

    /** Ejecuta varias operaciones en una sola transacción (commit si todo ok, rollback si hay error). */
    public static void runInTransaction(Runnable runnable) {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            try {
                runnable.run();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en transacción", e);
        }
    }
}

