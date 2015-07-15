package com.mengcraft.simpleorm;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    @Override
    public void onLoad() {
        getServer().getServicesManager().register(EbeanManager.class,
                EbeanManager.DEFAULT,
                this,
                ServicePriority.Normal);
    }

    @Override
    public void onEnable() {
        getCommand("simpleorm").setExecutor(new Debugger());
    }

}
