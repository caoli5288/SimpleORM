package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.mengcraft.simpleorm.lib.RefHelper;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public final class Reflect {

    public static void replace(Plugin proxy, EbeanServer in) throws Exception {
        Field server = JavaPlugin.class.getDeclaredField("ebean");
        server.setAccessible(true);
        server.set(proxy, in);
    }

    public static ClassLoader getLoader(Plugin plugin) throws Exception {
        return RefHelper.getField(plugin, "classLoader");
    }

}
