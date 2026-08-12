package com.ferreteria.services;

import com.ferreteria.database.DatabaseManager;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class LicenseService {

    private static final String LICENSE_FILE = "license.properties";
    private static final String CURRENT_ACCOUNTS_KEY = "module.current_accounts";
    private static final String EDITION_KEY = "edition";

    private final Properties properties;

    public LicenseService() {
        this.properties = loadProperties();
    }

    public boolean isCurrentAccountsEnabled() {
        return Boolean.parseBoolean(properties.getProperty(CURRENT_ACCOUNTS_KEY, "false"));
    }

    public String getEdition() {
        return properties.getProperty(EDITION_KEY, "basic").trim();
    }

    private Properties loadProperties() {
        Properties loaded = new Properties();
        for (Path path : candidatePaths()) {
            if (path == null || !Files.isRegularFile(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                loaded.load(input);
                return loaded;
            } catch (Exception ignored) {
            }
        }
        return loaded;
    }

    private Path[] candidatePaths() {
        return new Path[] {
                Paths.get(DatabaseManager.getDataFolder(), LICENSE_FILE),
                resolveApplicationPath(LICENSE_FILE),
                Paths.get(System.getProperty("user.dir", ""), LICENSE_FILE)
        };
    }

    private Path resolveApplicationPath(String fileName) {
        try {
            URI location = LicenseService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Paths.get(location);
            Path base = Files.isRegularFile(path) ? path.getParent() : path;
            return base == null ? null : base.resolve(fileName);
        } catch (Exception e) {
            return null;
        }
    }
}
