package com.mengcraft.simpleorm;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.mengcraft.simpleorm.lib.GsonUtils;
import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import com.mengcraft.simpleorm.lib.Reflector;
import com.mengcraft.simpleorm.provider.IDataSourceProvider;
import com.mengcraft.simpleorm.provider.IRedisProvider;
import com.mengcraft.simpleorm.redis.RedisProviders;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static com.mengcraft.simpleorm.lib.Tuple.tuple;

public class ORM extends JavaPlugin {

    public static final String PLAYER_METADATA_KEY = "ORM_METADATA";
    private static final int MAXIMUM_SIZE = Math.min(20, Runtime.getRuntime().availableProcessors() + 1);
    private static final GenericTrigger GENERIC_TRIGGER = new GenericTrigger();
    private static final ThreadLocal<Gson> JSON_LAZY = ThreadLocal.withInitial(GsonUtils::createJsonInBuk);
    private static RedisWrapper globalRedisWrapper;
    private static MongoWrapper globalMongoWrapper;
    private static HikariDataSource sharedSource;
    private static ORM plugin;
    private static IDataSourceProvider dataSourceProvider = new DataSourceProvider();
    private static IRedisProvider redisProvider;

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
        LibraryLoader.load(plugin, MavenLibrary.of("org.avaje:ebean:2.8.1"), true);
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        new MetricsLite(this);
        if (nil(globalRedisWrapper)) {
            if (redisProvider == null) {
                String redisUrl = getConfig().getString("redis.url", "");
                int max = getConfig().getInt("redis.max_conn", -1);
                redisProvider = RedisProviders.of(getConfig().getString("redis.master_name"), redisUrl, max);
            }
            globalRedisWrapper = new RedisWrapper(redisProvider);
        }
        if (nil(globalMongoWrapper)) {
            String url = getConfig().getString("mongo.url", "");
            if (!url.isEmpty()) {
                globalMongoWrapper = MongoWrapper.b(url);
            }
        }
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
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

    public static <T> T attr(Player player, @NonNull String key) {
        Preconditions.checkState(player.isOnline(), "player not online");
        if (player.hasMetadata(PLAYER_METADATA_KEY)) {
            Map<String, Object> metadata = (Map<String, Object>) player.getMetadata(PLAYER_METADATA_KEY).get(0).value();
            return (T) metadata.get(key);
        }
        return null;
    }

    public static <T> void attr(Player player, @NonNull String key, T value) {
        Preconditions.checkState(player.isOnline(), "player not online");
        Preconditions.checkState(Bukkit.isPrimaryThread(), "cannot modify player attributes async");
        if (player.hasMetadata(PLAYER_METADATA_KEY)) {
            Map<String, Object> metadata = (Map<String, Object>) player.getMetadata(PLAYER_METADATA_KEY).get(0).value();
            if (value == null) {
                metadata.remove(key);
            } else {
                metadata.put(key, value);
            }
        } else {
            if (value == null) {
                return;
            }
            Map<String, Object> metadata = new HashMap<>();
            player.setMetadata(PLAYER_METADATA_KEY, new FixedMetadataValue(plugin, metadata));
            metadata.put(key, value);
        }
    }

    @Deprecated
    public static <T> T attr(Player player, @NonNull String key, @NonNull Supplier<T> defaultValue) {
        Preconditions.checkState(player.isOnline(), "player not online");
        if (player.hasMetadata(PLAYER_METADATA_KEY)) {
            Map<String, Object> metadata = (Map<String, Object>) player.getMetadata(PLAYER_METADATA_KEY).get(0).value();
            if (metadata.containsKey(key)) {
                return (T) metadata.get(key);
            }
            Preconditions.checkState(Bukkit.isPrimaryThread(), "cannot modify player attributes async");
            T value = Objects.requireNonNull(defaultValue.get());
            metadata.put(key, value);
            return value;
        } else {
            Preconditions.checkState(Bukkit.isPrimaryThread(), "cannot modify player attributes async");
            T value = Objects.requireNonNull(defaultValue.get());
            Map<String, Object> metadata = new HashMap<>();
            player.setMetadata(PLAYER_METADATA_KEY, new FixedMetadataValue(plugin, metadata));
            metadata.put(key, value);
            return value;
        }
    }

    public static IDataSourceProvider getDataSourceProvider() {
        return dataSourceProvider;
    }

    public static void setDataSourceProvider(IDataSourceProvider dataSourceProvider) {
        Preconditions.checkState(!isFullyEnabled(), "Cannot be set after ORM enabled");
        ORM.dataSourceProvider = dataSourceProvider;
    }

    public static void setRedisProvider(IRedisProvider redisProvider) {
        Preconditions.checkState(!isFullyEnabled(), "Cannot be set after ORM enabled");
        ORM.redisProvider = redisProvider;
    }
}
