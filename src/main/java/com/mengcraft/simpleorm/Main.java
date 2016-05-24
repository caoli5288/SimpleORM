package com.mengcraft.simpleorm;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public class Main extends JavaPlugin {

    private final EbeanManager manager = EbeanManager.DEFAULT;

    @Override
    public void onLoad() {
        getServer().getServicesManager().register(EbeanManager.class,
                EbeanManager.DEFAULT,
                this,
                ServicePriority.Normal);
    }

    @Override
    public void onEnable() {
        getCommand("simpleorm").setExecutor(this);
        new MetricsLite(this).start();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
