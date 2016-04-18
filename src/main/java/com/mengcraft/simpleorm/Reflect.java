package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public final class Reflect {

    public static void replace(Plugin proxy, EbeanServer in) throws Exception {
        Field server = JavaPlugin.class.getDeclaredField("ebean");
        server.setAccessible(true);
        server.set(proxy, in);
    }

    public static ClassLoader getLoader(Plugin in) throws Exception {
        Field loader = JavaPlugin.class.getDeclaredField("classLoader");
        loader.setAccessible(true);
        return ClassLoader.class.cast(loader.get(in));
    }

}
