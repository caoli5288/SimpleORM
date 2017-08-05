package com.mengcraft.simpleorm;

import lombok.val;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class EbeanManager {

    public static final EbeanManager DEFAULT = new EbeanManager();

    public static final String URL = "jdbc:mysql://localhost/db";
    public static final String USERNAME = "user";
    public static final String PASSWORD = "passwd";

    final Map<String, EbeanHandler> map = new HashMap<>();

    private EbeanManager() {
    }

    public EbeanHandler getHandler(JavaPlugin plugin) {
        EbeanHandler out = map.get(plugin.getName());
        if (out == null) {
            map.put(plugin.getName(), out = build(plugin));
        }
        return out;
    }

    /**
     * @param name the name
     * @return the named handler, or {@code null} if absent
     */
    public EbeanHandler getHandler(String name) {
        return map.get(name);
    }

    private EbeanHandler build(JavaPlugin plugin) {
        val out = new EbeanHandler(plugin, true);

        String url = plugin.getConfig().getString("dataSource.url");
        String u = plugin.getConfig().getString("dataSource.userName");
        String password = plugin.getConfig().getString("dataSource.password");

        val driver = plugin.getConfig().getString("dataSource.driver");

        boolean b = false;

        if (url == null) {
            plugin.getConfig().set("dataSource.url", url = URL);
            b = true;
        }
        out.setUrl(url);

        if (u == null) {
            plugin.getConfig().set("dataSource.userName", u = USERNAME);
            b = true;
        }
        out.setUserName(u);

        if (password == null) {
            plugin.getConfig().set("dataSource.password", password = PASSWORD);
            b = true;
        }
        out.setPassword(password);

        if (b) {
            plugin.saveConfig();
        }

        if (!(driver == null)) {
            out.setDriver(driver);
        }

        return out;
    }

    static void shutdown(EbeanHandler db) {
        DEFAULT.map.remove(db.getPlugin().getName(), db);
    }

    @Deprecated
    public static void shutdown(JavaPlugin plugin) throws DatabaseException {
        val db = DEFAULT.map.get(plugin.getName());
        if (!(db == null)) {
            db.shutdown();
        }
    }

}
