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
        if (!ORM.isFullyEnabled()) {
            plugin.getLogger().warning("Try register db handler while ORM not fully enabled(Not depend on or register at onLoad?).");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + plugin.getName() + " try register db handler while ORM not fully enabled. This may cause unknown issues.");
        }
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

        String user = plugin.getConfig().getString("dataSource.user");
        if (user == null) {
            user = plugin.getConfig().getString("dataSource.userName");
        }

        String password = plugin.getConfig().getString("dataSource.password");

        String driver = plugin.getConfig().getString("dataSource.driver");

        boolean b = false;

        if (url == null) {
            plugin.getConfig().set("dataSource.url", url = EbeanManager.url);
            b = true;
        }
        out.setUrl(url);

        if (user == null) {
            plugin.getConfig().set("dataSource.user", user = EbeanManager.user);
            b = true;
        }
        out.setUser(user);

        if (password == null) {
            plugin.getConfig().set("dataSource.password", password = EbeanManager.password);
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

    @Deprecated
    public static void shutdown(JavaPlugin plugin) throws DatabaseException {
        val db = DEFAULT.map.get(plugin.getName());
        if (!(db == null)) {
            db.shutdown();
        }
    }

}
