package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.SneakyThrows;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Reflect {

    private static final Method SET_DATABASE_ENABLED = INIT_SET_DATABASE_ENABLED();
    private static final Field EBEAN = INIT_EBEAN();
    private static final Field JAVA_PLUGIN_classLoader = Utils.getAccessibleField(JavaPlugin.class, "classLoader");

    private static Method INIT_SET_DATABASE_ENABLED() {
        try {
            return PluginDescriptionFile.class.getMethod("setDatabaseEnabled", boolean.class);
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static Field INIT_EBEAN() {
        try {
            Field ebean = JavaPlugin.class.getDeclaredField("ebean");
            ebean.setAccessible(true);
            return ebean;
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }

    @SneakyThrows
    public static void setDatabaseEnabled(PluginDescriptionFile descriptionFile, boolean update) {
        if (SET_DATABASE_ENABLED != null) {
            SET_DATABASE_ENABLED.invoke(descriptionFile, update);
        }
    }

    @SneakyThrows
    public static void setEbeanServer(Plugin proxy, EbeanServer in) {
        if (EBEAN != null) {
            EBEAN.set(proxy, in);
        }
    }

    @SneakyThrows
    public static ClassLoader getLoader(Plugin plugin) {
        return (ClassLoader) JAVA_PLUGIN_classLoader.get(plugin);
    }

    public static boolean isLegacy() {
        return EBEAN != null;
    }
}
