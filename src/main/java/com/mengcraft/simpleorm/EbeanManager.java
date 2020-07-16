package com.mengcraft.simpleorm;

import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class EbeanManager {

    public static final EbeanManager DEFAULT = new EbeanManager();

    private static String url = "jdbc:mysql://localhost/db";
    private static String user = "user";
    private static String password = "passwd";

    final Map<String, EbeanHandler> map = new HashMap<>();

    private EbeanManager() {
    }

    public EbeanHandler getHandler(JavaPlugin plugin) {
        return getHandler(plugin, false);
    }

    public EbeanHandler getHandler(JavaPlugin plugin, boolean shared) {
        if (!ORM.isFullyEnabled()) {
            plugin.getLogger().warning("Try register db handler while ORM not fully enabled(Not depend on or register at onLoad?).");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + plugin.getName() + " try register db handler while ORM not fully enabled. This may cause unknown issues.");
        }
        EbeanHandler out = map.get(plugin.getName());
        if (out == null) {
            map.put(plugin.getName(), out = build(plugin, shared));
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

    private EbeanHandler build(JavaPlugin plugin, boolean shared) {
        if (shared || plugin.getConfig().getBoolean("dataSource.disabled", false)) {
            return new EbeanHandler(plugin, true, ORM.getSharedDs());
        }

        EbeanHandler handler = new EbeanHandler(plugin, true, null);
        String url = plugin.getConfig().getString("dataSource.url");
        String user = plugin.getConfig().getString("dataSource.user");
        if (user == null) {
            user = plugin.getConfig().getString("dataSource.userName");
        }
        String password = plugin.getConfig().getString("dataSource.password");

        if (url == null) {
            plugin.getConfig().set("dataSource.disabled", false);
            plugin.getConfig().set("dataSource.url", url = EbeanManager.getUrl());
            plugin.getConfig().set("dataSource.user", user = EbeanManager.getUser());
            plugin.getConfig().set("dataSource.password", password = EbeanManager.getPassword());
            plugin.saveConfig();
        } else {
            String driver = plugin.getConfig().getString("dataSource.driver");
            if (driver != null) {
                handler.setDriver(driver);
            }
        }

        handler.setUrl(url);
        handler.setUser(user);
        handler.setPassword(password);

        return handler;
    }

    static void unHandle(EbeanHandler db) {
        DEFAULT.map.remove(db.getPlugin().getName(), db);
    }

    public static void setUrl(String url) {
        EbeanManager.url = url;
    }

    public static void setUser(String user) {
        EbeanManager.user = user;
    }

    public static void setPassword(String password) {
        EbeanManager.password = password;
    }

    public static String getUrl() {
        return url;
    }

    public static String getUser() {
        return user;
    }

    public static String getPassword() {
        return password;
    }

    @Deprecated
    public static void shutdown(JavaPlugin plugin) throws DatabaseException {
        val db = DEFAULT.map.get(plugin.getName());
        if (!(db == null)) {
            db.shutdown();
        }
    }

}
