package com.mengcraft.simpleorm;

import com.mengcraft.simpleorm.lib.LibraryLoader;
import com.mengcraft.simpleorm.lib.MavenLibrary;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        val manager = EbeanManager.DEFAULT;
        val l = manager.map;
        if (!l.isEmpty()) {
            l.forEach((key, handler) -> sender.sendMessage("[" + key + "] -> " + handler));
        }
        return true;
    }

}
