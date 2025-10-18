package com.mengcraft.simpleorm;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.mengcraft.simpleorm.driver.ConfiguredDatabaseDriver;
import com.mengcraft.simpleorm.driver.DatabaseDriverRegistry;
import com.mengcraft.simpleorm.driver.DatabaseDriverInfo;
import com.mengcraft.simpleorm.lib.GsonUtils;
import com.mengcraft.simpleorm.lib.MavenLibs;
import com.mengcraft.simpleorm.lib.Utils;
import com.mengcraft.simpleorm.provider.IDataSourceProvider;
import com.mengcraft.simpleorm.provider.IRedisProvider;
import com.mengcraft.simpleorm.redis.RedisProviders;
import com.mengcraft.simpleorm.serializable.SerializableTypes;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;

@ExtensionMethod(Utils.class)
public class ORM extends JavaPlugin implements Executor {

    public static final String PLAYER_METADATA_KEY = "ORM_METADATA";
    private static final int MAXIMUM_SIZE = Math.min(20, Runtime.getRuntime().availableProcessors() + 1);
    private static final GenericTrigger GENERIC_TRIGGER = new GenericTrigger();
    private static final ThreadLocal<Gson> JSON_LAZY = ThreadLocal.withInitial(GsonUtils::createJsonInBuk);
    private static RedisWrapper globalRedisWrapper;
    private static MongoWrapper globalMongoWrapper;
    private static Map<String, RedisWrapper> redisAliases;
    private static Map<String, MongoWrapper> mongodbAliases;
    private static volatile DataSource sharedDs;
    static ORM plugin;
    private static IDataSourceProvider dataSourceProvider;
    @Getter
    private static FluxWorkers workers;

    @Override
    public void onLoad() {
        plugin = this;
        loadLibrary(this);
        saveDefaultConfig();
        workers = new FluxWorkers(getConfig().getInt("cpus", 8));
        dataSourceProvider = new DataSourceManager().let(it -> it.load(this));

        getServer().getServicesManager().register(EbeanManager.class,
                EbeanManager.DEFAULT,
                this,
                ServicePriority.Normal);
    }

    @Override
    @SneakyThrows
    public void saveDefaultConfig() {
        super.saveDefaultConfig();
        File f = new File(getDataFolder(), "drivers.yml");
        if (!f.exists()) {
            saveResource("drivers.yml", false);
        }
        try (BufferedReader load = Files.newReader(f, StandardCharsets.UTF_8)) {
            Map<String, Map<String, Object>> loadMap = Utils.YAML.load(load);
            for (Map.Entry<String, Map<String, Object>> let : loadMap.entrySet()) {
                DatabaseDriverInfo info = ORM.deserialize(DatabaseDriverInfo.class, let.getValue());
                DatabaseDriverRegistry.register(new ConfiguredDatabaseDriver(let.getKey(), info));
            }
        }
    }

    public static void loadLibrary(JavaPlugin plugin) {
        try {
            Class.forName("com.avaje.ebean.EbeanServer");
        } catch (ClassNotFoundException e) {
            MavenLibs.of("org.avaje:ebean:2.8.1").load();
        }
        try {
            Class.forName("com.zaxxer.hikari.HikariDataSource");
        } catch (ClassNotFoundException e) {
            MavenLibs.of("com.zaxxer:HikariCP:4.0.3").load();
        }
        try {
            Class<?> cls = Class.forName("redis.clients.jedis.Jedis");
            plugin.getLogger().info(String.format("Jedis URL{%s}", cls.getProtectionDomain().getCodeSource().getLocation()));
        } catch (ClassNotFoundException e) {
            MavenLibs.of("redis.clients:jedis:3.8.0").load();
        }
        try {
            Class.forName("com.mongodb.MongoClient");
        } catch (ClassNotFoundException e) {
            MavenLibs.of("org.mongodb:mongo-java-driver:3.12.14").load();
        }
        plugin.getLogger().info("ORM lib load okay!");
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        new MetricsLite(this);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        if (nil(globalRedisWrapper)) {
            globalRedisWrapper = loadRedis(getConfig().getConfigurationSection("redis"));
        }
        if (nil(globalMongoWrapper)) {
            globalMongoWrapper = loadMongodb(getConfig().getConfigurationSection("mongo"));
        }
        // init aliases
        // redis
        redisAliases = Maps.newHashMap();
        getConfig().getKeys(false).stream()
                .filter(l -> l.startsWith("redis") && validAliases(getConfig().getStringList(l + ".aliases")))
                .forEach(l -> loadRedisAliases(l, loadRedis(getConfig().getConfigurationSection(l)), getConfig().getStringList(l + ".aliases")));
        // mongodb
        mongodbAliases = Maps.newHashMap();
        getConfig().getKeys(false).stream()
                        .filter(l -> l.startsWith("mongo") && validAliases(getConfig().getStringList(l + ".aliases")))
                                .forEach(l -> loadMongodbAliases(l, loadMongodb(getConfig().getConfigurationSection(l)), getConfig().getStringList(l + ".aliases")));
        getLogger().info("Welcome!");
    }

    static void loadMongodbAliases(String key, MongoWrapper alias, List<String> list) {
        if (alias == null) {
            // log
            plugin.getLogger().log(Level.WARNING, String.format("Mongodb %s is invalid", key));
        } else {
            for (String line : list) {
                mongodbAliases.put(line, alias);
                plugin.getLogger().info(String.format("Configured mongodb alias %s to %s", line, key));
            }
        }
    }

    static boolean validAliases(List<String> list) {
        if (list.isEmpty()) {
            return false;
        }
        return list.stream()
                .anyMatch(l -> Bukkit.getPluginManager().getPlugin(l) != null);
    }

    static void loadRedisAliases(String key, RedisWrapper alias, List<String> list) {
        if (alias == null) {
            // log
            plugin.getLogger().log(Level.WARNING, String.format("Redis %s is invalid", key));
        } else {
            for (String line : list) {
                redisAliases.put(line, alias);
                plugin.getLogger().info(String.format("Configured redis alias %s to %s", line, key));
            }
        }
    }

    static MongoWrapper loadMongodb(ConfigurationSection cfg) {
        String url = cfg.getString("url");
        if (Utils.isNotEmpty(url)) {
            return MongoWrapper.create(url, cfg);
        }
        return null;
    }

    static RedisWrapper loadRedis(ConfigurationSection cfg) {
        String url = cfg.getString("url");
        if (Utils.isNotEmpty(url)) {
            int max = cfg.getInt("max_conn", -1);
            return new RedisWrapper(RedisProviders.of(cfg.getString("master_name"), url, max, cfg.getString("password")));
        }
        return null;
    }

    @Override
    public void onDisable() {
        workers.close();
        try {
            workers.awaitClose(Integer.MAX_VALUE);
        } catch (InterruptedException e) {
            getLogger().log(Level.WARNING, "Error occurred while await workers closed", e);
        }
        globalRedisWrapper.let(it -> {
            it.close();
            globalRedisWrapper = null;
        });
        globalMongoWrapper.let(it -> {
            it.close();
            globalMongoWrapper = null;
        });
        redisAliases.forEach((__, l) -> l.close());
        redisAliases = null;
        mongodbAliases.forEach((__, l) -> l.close());
        mongodbAliases = null;
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

    public static RedisWrapper getRedisWrapper(@NotNull Plugin pl) {
        return Optional.ofNullable(redisAliases.get(pl.getName()))
                .orElse(globalRedisWrapper());
    }

    public static MongoWrapper getMongoWrapper(@NotNull Plugin pl) {
        return Optional.ofNullable(mongodbAliases.get(pl.getName()))
                .orElse(globalMongoWrapper());
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

    /**
     * @deprecated It's maybe not always HikariDataSource because of the opened setSharedDs function.
     */
    public static HikariDataSource getSharedSource() {
        return (HikariDataSource) getSharedDs();
    }

    public static DataSource getSharedDs() {// SharedDs
        if (sharedDs == null) {
            synchronized (ORM.class) {
                if (sharedDs == null) {
                    sharedDs = getDataSourceProvider().getDataSource(getDataHandler(plugin));
                }
            }
        }
        return sharedDs;
    }

    public static void setSharedDs(@NonNull DataSource sharedDs) {
        Preconditions.checkState(Bukkit.isPrimaryThread(), "async set shared data-source");
        ORM.sharedDs = sharedDs;
    }

    public static DataSource getDataSource(JavaPlugin from) {
        return getDataSourceProvider().getDataSource(getDataHandler(from));
    }

    public static Map<String, Object> serialize(Object any) {
        Preconditions.checkArgument(!(any instanceof Collection), "serialize Collection");
        if (any instanceof ConfigurationSerializable) {
            return ((ConfigurationSerializable) any).serialize();
        }
        return (Map<String, Object>) GsonUtils.dump(json().toJsonTree(any));
    }

    public static Gson json() {
        return JSON_LAZY.get();
    }

    public static <T> T deserialize(Class<T> clz, Map<String, Object> map) {
        return (T) SerializableTypes.asDeserializer(clz).deserialize(clz, map);
    }

    @NotNull
    public static <T> T deserialize(@NotNull Class<T> cls, @NotNull File file) throws IOException {
        try (BufferedReader buff = Files.newReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> load = Utils.YAML.load(buff);
            return deserialize(cls, load);
        }
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
        // Can overwrite by others
        globalRedisWrapper = new RedisWrapper(redisProvider);
    }

    public static CompletableFuture<Void> enqueue(Runnable runnable) {
        return Utils.enqueue(workers.of(), runnable);
    }

    public static CompletableFuture<Void> enqueue(String ns, Runnable runnable) {
        return Utils.enqueue(workers.of(ns), runnable);
    }

    public static <T> CompletableFuture<T> enqueue(Supplier<T> supplier) {
        return Utils.enqueue(workers.of(), supplier);
    }

    public static <T> CompletableFuture<T> enqueue(String ns, Supplier<T> supplier) {
        return Utils.enqueue(workers.of(ns), supplier);
    }

    public static CompletableFuture<Void> sync(Runnable runnable) {
        return Utils.enqueue(workers.ofServer(), runnable);
    }

    public static <T> CompletableFuture<T> sync(Supplier<T> supplier) {
        return Utils.enqueue(workers.ofServer(), supplier);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (Bukkit.isPrimaryThread()) {
            command.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, command);
        }
    }
}
