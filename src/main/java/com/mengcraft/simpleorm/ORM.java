package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.google.gson.Gson;
import com.mengcraft.simpleorm.lib.JsonHelper;
import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import com.mengcraft.simpleorm.lib.Reflector;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.persistence.Entity;
import java.util.Map;
import java.util.Objects;

import static com.mengcraft.simpleorm.lib.Tuple.tuple;

public class ORM extends JavaPlugin {

    private static EbeanHandler globalHandler;
    private static RedisWrapper globalRedisWrapper;
    private static MongoWrapper globalMongoWrapper;
    private static ThreadLocal<Gson> jsonLazy = ThreadLocal.withInitial(JsonHelper::createJsonInBuk);

    @Override
    public void onLoad() {
        loadLibrary(this);
        saveDefaultConfig();

        EbeanManager.url = getConfig().getString("dataSource.url", "jdbc:mysql://localhost/db");
        EbeanManager.user = getConfig().getString("dataSource.user", "root");
        EbeanManager.password = getConfig().getString("dataSource.password", "wowsuchpassword");

        getServer().getServicesManager().register(EbeanManager.class,
                EbeanManager.DEFAULT,
                this,
                ServicePriority.Normal);

        setEnabled(true);
    }

    public static void loadLibrary(JavaPlugin plugin) {
        try {
            Class<?> loaded = Bukkit.class.getClassLoader().loadClass("com.avaje.ebean.EbeanServer");
            if (loaded == null) {
                loadExtLibrary(plugin);
            }
        } catch (ClassNotFoundException e) {
            loadExtLibrary(plugin);
        }
        plugin.getLogger().info("ORM lib load okay!");
    }

    private static void loadExtLibrary(JavaPlugin plugin) {
        LibraryLoader.load(plugin, MavenLibrary.of("org.avaje:ebean:2.8.1"), true);
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        new MetricsLite(this);
        if (!nil(globalHandler)) {
            globalHandler.initialize();
            globalHandler.install(true);
        }
        if (nil(globalRedisWrapper)) {
            String redisUrl = getConfig().getString("redis.url", "");
            if (!redisUrl.isEmpty()) {
                int max = getConfig().getInt("redis.max_conn", -1);
                globalRedisWrapper = RedisWrapper.b(getConfig().getString("redis.master_name"), redisUrl, max);
            }
        }
        if (nil(globalMongoWrapper)) {
            String url = getConfig().getString("mongo.url", "");
            if (!url.isEmpty()) {
                globalMongoWrapper = MongoWrapper.b(url);
            }
        }
        getLogger().info("Welcome!");
    }

    /**
     * Plz call this method in {@link Plugin#onLoad()}.
     *
     * @param input the entity bean class
     */
    public static synchronized void global(Class<?> input) {
        ORM plugin = JavaPlugin.getPlugin(ORM.class);
        if (plugin.isEnabled()) {
            throw new IllegalStateException("isEnabled");
        }

        Entity annotation = input.getAnnotation(Entity.class);
        if (annotation == null) {
            throw new IllegalStateException(input + " is not @Entity");
        }

        if (globalHandler == null) {
            globalHandler = EbeanManager.DEFAULT.getHandler(plugin);
        }

        globalHandler.define(input);
    }

    /**
     * Plz call this method in or after {@link Plugin#onEnable()}.
     *
     * @return the global server
     */
    public static EbeanServer globalDataServer() {
        return globalHandler.getServer();// fast fail
    }

    public static RedisWrapper globalRedisWrapper() {
        return Objects.requireNonNull(globalRedisWrapper);
    }

    public static MongoWrapper globalMongoWrapper() {
        return Objects.requireNonNull(globalMongoWrapper);
    }

    public static EbeanHandler getDataHandler(JavaPlugin plugin) {
        return EbeanManager.DEFAULT.getHandler(plugin);
    }

    public static boolean nil(Object any) {
        return any == null;
    }

    public static Gson json() {
        return jsonLazy.get();
    }

    public static Map<String, Object> serialize(Object any) {
        if (any instanceof ConfigurationSerializable) {
            return ((ConfigurationSerializable) any).serialize();
        }
        return (Map<String, Object>) JsonHelper.primitive(json().toJsonTree(any));
    }

    public static <T> T deserialize(Class<T> clz, Map<String, Object> map) {
        if (ConfigurationSerializable.class.isAssignableFrom(clz)) {
            try {
                return Reflector.object(clz, tuple(Map.class, map));
            } catch (Exception ignored) {
                return Reflector.invoke(clz, "deserialize", tuple(Map.class, map));
            }
        }
        val json = json();
        return json.fromJson(json.toJsonTree(map), clz);
    }

}
