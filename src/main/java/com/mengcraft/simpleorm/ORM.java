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
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.persistence.Entity;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static com.mengcraft.simpleorm.lib.Tuple.tuple;

public class ORM extends JavaPlugin {

    private static EbeanHandler globalHandler;
    private static RedisWrapper globalRedisWrapper;
    private static MongoWrapper globalMongoWrapper;

    private static volatile Gson lazyJson;

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
        getCommand("simpleorm").setExecutor(this);
        new MetricsLite(this);
        if (!nil(globalHandler)) {
            globalHandler.initialize();
            globalHandler.install(true);
        }
        if (nil(globalRedisWrapper)) {
            String redisUrl = getConfig().getString("redis.url", "");
            if (!redisUrl.isEmpty()) {
                int max = getConfig().getInt("redis.max_conn", -1);
                globalRedisWrapper = RedisWrapper.b(redisUrl, max);
            }
        }
        if (nil(globalMongoWrapper)) {
            String url = getConfig().getString("mongo.url", "");
            if (!url.isEmpty()) {
                globalMongoWrapper = MongoWrapper.b(url);
            }
        }
    }

    public boolean onCommand(CommandSender who, Command command, String label, String[] input) {
        if (input.length < 1) {
            for (val executor : SubExecutor.values()) {
                who.sendMessage('/' + label + ' ' + executor.usage);
            }
        } else {
            try {
                Iterator<String> itr = Arrays.asList(input).iterator();
                SubExecutor.valueOf(itr.next().toUpperCase()).exec(who, itr);
                return true;
            } catch (Exception exc) {
                who.sendMessage(ChatColor.RED + exc.toString());
            }
        }
        return false;
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
        Gson json = lazyJson;
        if (nil(json)) {
            synchronized (ORM.class) {
                if (nil(lazyJson)) {
                    lazyJson = new Gson();
                }
                return lazyJson;
            }
        }
        return json;
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

    enum SubExecutor {

        LIST("list") {
            void exec(CommandSender who, Iterator<String> itr) {
                Map<String, EbeanHandler> all = EbeanManager.DEFAULT.map;
                if (!all.isEmpty()) {
                    all.forEach((key, handler) -> who.sendMessage("[" + key + "] -> " + handler));
                }
            }
        },

        REMOVE("remove <plugin_name>") {
            void exec(CommandSender who, Iterator<String> itr) {
                EbeanHandler remove = EbeanManager.DEFAULT.map.remove(itr.next());
                who.sendMessage(remove == null ? "handle not found" : "okay");
            }
        },

        REMOVE_ALL("remove_all") {
            void exec(CommandSender who, Iterator<String> itr) {
                EbeanManager.DEFAULT.map.clear();
                who.sendMessage("okay");
            }
        };

        private final String usage;

        SubExecutor(String usage) {
            this.usage = usage;
        }

        void exec(CommandSender who, Iterator<String> itr) {
            throw new AbstractMethodError("exec");
        }

    }

}
