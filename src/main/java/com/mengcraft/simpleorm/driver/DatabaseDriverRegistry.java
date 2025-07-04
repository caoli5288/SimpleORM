package com.mengcraft.simpleorm.driver;

import com.google.common.collect.Maps;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

public class DatabaseDriverRegistry {

    private static final Map<String, IDatabaseDriver> DRIVERS = Maps.newHashMap();

    public static void register(IDatabaseDriver driver) {
        DRIVERS.put(driver.protocol(), driver);
    }

    public static String filter(String jdbc) {
        URI uri = URI.create(jdbc);
        if (!Objects.equals(uri.getScheme(), "jdbc")) {
            throw new IllegalArgumentException("Invalid JDBC URL format. Expected 'jdbc:protocol://...', but got: [" +
                    jdbc +
                    "]");
        }
        uri = URI.create(uri.getSchemeSpecificPart());
        IDatabaseDriver driver = DRIVERS.get(uri.getScheme());
        if (driver == null) {
            throw new IllegalArgumentException("Invalid JDBC Driver protocol [" +
                    uri.getScheme() +
                    "]. Expected [" +
                    String.join(", ", DRIVERS.keySet()) +
                    "]");
        }
        driver.load();
        return jdbc;
    }
}
