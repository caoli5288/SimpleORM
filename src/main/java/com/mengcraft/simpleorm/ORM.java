package com.mengcraft.simpleorm;

import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import lombok.SneakyThrows;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

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
            plugin.getClass().getClassLoader().loadClass("com.avaje.ebean.EbeanServer");
        } catch (ClassNotFoundException e) {
            LibraryLoader.load(plugin, MavenLibrary.of("org.avaje:ebean:2.8.1"));
        }
    }

    @Override
    @SneakyThrows
    public void onEnable() {
        saveDefaultConfig();

        getCommand("simpleorm").setExecutor(this);
        new MetricsLite(this).start();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EbeanManager manager = EbeanManager.DEFAULT;
        Collection<EbeanHandler> handlers = manager.handers();
        if (handlers.size() != 0) {
            for (EbeanHandler handler : manager.handers()) {
                sender.sendMessage("[SimpleORM] " + handler);
            }
        } else {
            sender.sendMessage("[SimpleORM] No registered handler!");
        }
        return true;
    }

}
