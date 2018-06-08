package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

import static com.mengcraft.simpleorm.lib.Reflector.getField;

public final class Reflect {

    public static void replace(Plugin proxy, EbeanServer in) throws Exception {
        Field server = JavaPlugin.class.getDeclaredField("ebean");
        server.setAccessible(true);
        server.set(proxy, in);
    }

    public static ClassLoader getLoader(Plugin plugin) throws Exception {
        return getField(plugin, "classLoader");
    }

}
