package com.mengcraft.simpleorm;

import com.mengcraft.simpleorm.lib.Utils;
import lombok.experimental.ExtensionMethod;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Use {@link ORM}'s static functions
 */
@ExtensionMethod(Utils.class)
public class EbeanManager {

    public static final EbeanManager DEFAULT = new EbeanManager();

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
        EbeanHandler handler = new EbeanHandler(plugin, true, null);
        initialize(plugin, handler);
        return handler;
    }

    static void unHandle(EbeanHandler db) {
        DEFAULT.map.remove(db.getPlugin().getName(), db);
    }

    @Deprecated
    public static void shutdown(JavaPlugin plugin) throws DatabaseException {
        val db = DEFAULT.map.get(plugin.getName());
        if (!(db == null)) {
            db.shutdown();
        }
    }

    public void initialize(Plugin plugin, EbeanHandler handler) {
        String url = plugin.getConfig().getString("dataSource.url");
        String user = plugin.getConfig().getString("dataSource.user");
        String password = plugin.getConfig().getString("dataSource.password");
        if (url.isNullOrEmpty() || user.isNullOrEmpty() || password.isNullOrEmpty()) {
            return;
        }

        handler.setUrl(url);
        handler.setUser(user);
        handler.setPassword(password);
        String driver = plugin.getConfig().getString("dataSource.driver");
        if (driver != null) {
            handler.setDriver(driver);
        }
    }
}
