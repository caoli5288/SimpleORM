package com.mengcraft.simpleorm;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    
    @Override
    public void onEnable() {
        getCommand("simpleorm").setExecutor(new Debugger());
    }
    
    public static class Default {

        public static final Object PASSWORD = "testPassword";
        public static final Object USER_NAME = "testUserName";
        public static final Object DRIVER = "com.mysql.jdbc.Driver";
        public static final String URL = "jdbc:mysql://localhost/db";
        
    }

}
