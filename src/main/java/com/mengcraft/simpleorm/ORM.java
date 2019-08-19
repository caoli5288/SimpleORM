package com.mengcraft.simpleorm;

import com.google.gson.Gson;
import com.mengcraft.simpleorm.lib.GsonUtils;
import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import com.mengcraft.simpleorm.lib.Reflector;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static com.mengcraft.simpleorm.lib.Tuple.tuple;

public class ORM extends JavaPlugin {

    private static final int MAXIMUM_SIZE = Math.min(20, Runtime.getRuntime().availableProcessors() + 1);
    private static final GenericTrigger GENERIC_TRIGGER = new GenericTrigger();
    private static final ThreadLocal<Gson> JSON_LAZY = ThreadLocal.withInitial(GsonUtils::createJsonInBuk);
    private static RedisWrapper globalRedisWrapper;
    private static MongoWrapper globalMongoWrapper;
    private static HikariDataSource sharedSource;
    private static ORM plugin;

    @Override
    public void onLoad() {
        plugin = this;
        loadLibrary(this);
        saveDefaultConfig();

        EbeanManager.setUrl(getConfig().getString("dataSource.url", "jdbc:mysql://localhost/db"));
        EbeanManager.setUser(getConfig().getString("dataSource.user", "root"));
        EbeanManager.setPassword(getConfig().getString("dataSource.password", "wowsuchpassword"));

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
        LibraryLoader.load(plugin, MavenLibrary.of("org.avaje:ebean:2.8.1"));
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        new MetricsLite(this);
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

    public static boolean nil(Object any) {
        return any == null;
    }

    public static int getMaximumSize() {
        return MAXIMUM_SIZE;
    }

    public static boolean isFullyEnabled() {
        return plugin.isEnabled();
    }

    public static RedisWrapper globalRedisWrapper() {
        return Objects.requireNonNull(globalRedisWrapper);
    }

    public static MongoWrapper globalMongoWrapper() {
        return Objects.requireNonNull(globalMongoWrapper);
    }

    public static EbeanHandler getDataHandler(JavaPlugin plugin) {
        return getDataHandler(plugin, false);
    }

    public static EbeanHandler getDataHandler(JavaPlugin plugin, boolean shared) {
        return EbeanManager.DEFAULT.getHandler(plugin, shared);
    }

    public static GenericTrigger getGenericTrigger() {
        return GENERIC_TRIGGER;
    }

    public synchronized static HikariDataSource getSharedSource() {
        if (sharedSource == null) {
            sharedSource = new HikariDataSource();
            sharedSource.setPoolName("simple_shared");
            sharedSource.setJdbcUrl(plugin.getConfig().getString("dataSource.url"));
            sharedSource.setUsername(plugin.getConfig().getString("dataSource.user"));
            sharedSource.setPassword(plugin.getConfig().getString("dataSource.password"));
            sharedSource.setAutoCommit(false);
            sharedSource.setMinimumIdle(1);
            sharedSource.setMaximumPoolSize(getMaximumSize());
        }
        return sharedSource;
    }

    public static Map<String, Object> serialize(Object any) {
        if (any instanceof ConfigurationSerializable) {
            return ((ConfigurationSerializable) any).serialize();
        }
        return (Map<String, Object>) GsonUtils.dump(json().toJsonTree(any));
    }

    public static Gson json() {
        return JSON_LAZY.get();
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

    public static <T> T attr(Player player, String key, Supplier<T> defaultValue) {
        if (player.hasMetadata(key)) {
            return (T) player.getMetadata(key).get(0).value();
        }
        if (defaultValue == null) {
            return null;
        } else {
            T value = Objects.requireNonNull(defaultValue.get());
            player.setMetadata(key, new FixedMetadataValue(plugin, value));
            return value;
        }
    }
}
