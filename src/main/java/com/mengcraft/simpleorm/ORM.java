package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.persistence.Entity;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ORM extends JavaPlugin {

    private static Set<Class<?>> global;
    private static EbeanHandler globalHandler;

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
        new MetricsLite(this).start();
        if (!(global == null) && globalHandler == null) {// always not empty if not null
            globalHandler = EbeanManager.DEFAULT.getHandler(this);
            global.forEach(globalHandler::define);
            globalHandler.initialize();
            globalHandler.install(true);
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
    public static void global(Class<?> input) {
        Entity annotation = input.getAnnotation(Entity.class);
        if (annotation == null) throw new IllegalStateException(input + " NOT an entity class");

        if (global == null) global = new HashSet<>();
        global.add(input);
    }

    /**
     * Plz call this method in or after {@link Plugin#onEnable()}.
     *
     * @return the global server
     */
    public static EbeanServer globalDataServer() {
        return globalHandler.getServer();// fast fail
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
