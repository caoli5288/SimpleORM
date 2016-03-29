package com.mengcraft.simpleorm;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EbeanManager {

    public static final EbeanManager DEFAULT = new EbeanManager();

    private final Map<String, EbeanHandler> map;

    private EbeanManager() {
        this.map = new HashMap<>();
    }

    public EbeanHandler getHandler(Plugin proxy) {
        EbeanHandler out = map.get(proxy.getName());
        if (out == null || out.getProxy() != proxy) {
            map.put(proxy.getName(), out = a(proxy));
        }
        return out;
    }

    public EbeanHandler getHandler(String name) {
        if (map.get(name) == null) {
            throw new NullPointerException("#3 NOT HANDLED!");
        }
        return map.get(name);
    }

    public void setHandler(JavaPlugin proxy, EbeanHandler handler) {
        map.put(proxy.getName(), handler);
    }

    public Collection<EbeanHandler> handers() {
        return new ArrayList<>(map.values());
    }

    public boolean hasHandler(JavaPlugin proxy) {
        return map.get(proxy.getName()) != null;
    }

    private EbeanHandler a(Plugin proxy) {
        EbeanHandler handler = new EbeanHandler(proxy);

        String driver = proxy.getConfig().getString("dataSource.driver");
        String url = proxy.getConfig().getString("dataSource.url");
        String userName = proxy.getConfig().getString("dataSource.userName");
        String password = proxy.getConfig().getString("dataSource.password");

        if (driver != null) {
            handler.setDriver(driver);
        } else {
            proxy.getConfig().set("dataSource.driver", DRIVER);
            proxy.saveConfig();
        }

        if (url != null) {
            handler.setUrl(url);
        } else {
            proxy.getConfig().set("dataSource.url", URL);
            proxy.saveConfig();
        }

        if (userName != null) {
            handler.setUserName(userName);
        } else {
            proxy.getConfig().set("dataSource.userName", USER_NAME);
            proxy.saveConfig();
        }

        if (password != null) {
            handler.setPassword(password);
        } else {
            proxy.getConfig().set("dataSource.password", PASSWORD);
            proxy.saveConfig();
        }

        return handler;
    }

    public static final String PASSWORD = "testPassword";
    public static final String USER_NAME = "testUserName";
    public static final String DRIVER = "com.mysql.jdbc.Driver";
    public static final String URL = "jdbc:mysql://localhost/db";

}
