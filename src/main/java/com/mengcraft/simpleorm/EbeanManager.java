package com.mengcraft.simpleorm;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EbeanManager {

    public static final String URL = "jdbc:mysql://localhost/db";
    public static final String USER_NAME = "testUserName";
    public static final String PASSWORD = "testPassword";

    public static final EbeanManager DEFAULT = new EbeanManager();

    private final Map<String, EbeanHandler> map = new HashMap<>();

    private EbeanManager() {
    }

    public EbeanHandler getHandler(JavaPlugin proxy) {
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

    private EbeanHandler a(JavaPlugin proxy) {
        EbeanHandler handler = new EbeanHandler(proxy);

        String url = proxy.getConfig().getString("dataSource.url");
        String userName = proxy.getConfig().getString("dataSource.userName");
        String password = proxy.getConfig().getString("dataSource.password");

        boolean b = false;

        if (url == null) {
            proxy.getConfig().set("dataSource.url", url = URL);
            b = true;
        }
        handler.setUrl(url);

        if (userName == null) {
            proxy.getConfig().set("dataSource.userName", userName = USER_NAME);
            b = true;
        }
        handler.setUserName(userName);

        if (password == null) {
            proxy.getConfig().set("dataSource.password", password = PASSWORD);
            b = true;
        }
        handler.setPassword(password);

        if (b) {
            proxy.saveConfig();
        }

        return handler;
    }

}
