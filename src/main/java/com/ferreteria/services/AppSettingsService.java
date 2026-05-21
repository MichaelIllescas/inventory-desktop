package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;
import com.ferreteria.models.AppSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AppSettingsService {

    private static final String KEY_NAME    = "business_name";
    private static final String KEY_ADDRESS = "business_address";
    private static final String KEY_PHONE   = "business_phone";
    private static final String KEY_CUIT    = "business_cuit";
    private static final String KEY_LOGO    = "business_logo_path";

    public AppSettings load() {
        AppSettings s = new AppSettings();
        String sql = "SELECT key, value FROM app_settings";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("key");
                String val = rs.getString("value");
                switch (key) {
                    case KEY_NAME    -> s.setBusinessName(val);
                    case KEY_ADDRESS -> s.setBusinessAddress(val);
                    case KEY_PHONE   -> s.setBusinessPhone(val);
                    case KEY_CUIT    -> s.setBusinessCuit(val);
                    case KEY_LOGO    -> s.setLogoPath(val);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar configuración", e);
        }
        return s;
    }

    public void save(AppSettings s) {
        String sql = "INSERT INTO app_settings(key, value) VALUES(?, ?) " +
                     "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        Connection conn = DatabaseManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            upsert(stmt, KEY_NAME,    s.getBusinessName());
            upsert(stmt, KEY_ADDRESS, s.getBusinessAddress());
            upsert(stmt, KEY_PHONE,   s.getBusinessPhone());
            upsert(stmt, KEY_CUIT,    s.getBusinessCuit());
            upsert(stmt, KEY_LOGO,    s.getLogoPath());
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar configuración", e);
        }
    }

    private void upsert(PreparedStatement stmt, String key, String value) throws SQLException {
        stmt.setString(1, key);
        stmt.setString(2, value == null ? "" : value);
        stmt.executeUpdate();
    }
}
