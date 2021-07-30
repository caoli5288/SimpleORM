package com.mengcraft.simpleorm.driver;

import com.mengcraft.simpleorm.lib.MavenLibs;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class IDatabaseDriver {

    protected abstract String clazz();

    protected abstract String protocol();

    protected abstract String description();

    private void load() {
        try {
            Class.forName(clazz());
        } catch (ClassNotFoundException e) {
            MavenLibs.of(description()).load();
            load();
        }
    }

    public static String filter(String jdbc) {
        URI uri = URI.create(jdbc);
        if (!Objects.equals(uri.getScheme(), "jdbc")) {
            throw new IllegalArgumentException(jdbc + " is not valid jdbc url");
        }
        uri = URI.create(uri.getSchemeSpecificPart());
        IDatabaseDriver driver = Registry.__.get(uri.getScheme());
        if (driver == null) {
            return jdbc;
        }
        driver.load();
        return jdbc;
    }

    public static class Registry {

        private static final Map<String, IDatabaseDriver> __ = new HashMap<>();

        static {
            register(new H2Driver());
            register(new PostgreDriver());
            register(new MySqlDriver());
        }

        private static void register(IDatabaseDriver driver) {
            __.put(driver.protocol(), driver);
        }
    }

}
