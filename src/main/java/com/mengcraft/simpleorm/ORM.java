package com.mengcraft.simpleorm;

import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Iterator;

public class ORM extends JavaPlugin {

    @Override
    public void onLoad() {
        loadLibrary(this);

        getServer().getServicesManager().register(EbeanManager.class,
                EbeanManager.DEFAULT,
                this,
                ServicePriority.Normal);
    }

    public static void loadLibrary(JavaPlugin plugin) {
        try {
            Class.forName("com.avaje.ebean.EbeanServer");
        } catch (ClassNotFoundException e) {
            LibraryLoader.load(plugin, MavenLibrary.of("org.avaje:ebean:2.8.1"));
        }
        plugin.getLogger().info("ORM lib load okay!");
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        getCommand("simpleorm").setExecutor(this);
        new MetricsLite(this).start();
    }

    public boolean onCommand(CommandSender who, Command command, String label, String[] input) {
        if (input.length < 1) {
            for (val executor : SubExecutor.values()) {
                who.sendMessage('/' + label + executor.usage);
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

    enum SubExecutor {

        LIST("list") {
            void exec(CommandSender who, Iterator<String> itr) {
                val all = EbeanManager.DEFAULT.map;
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
