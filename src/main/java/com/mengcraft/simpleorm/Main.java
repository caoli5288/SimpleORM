package com.mengcraft.simpleorm;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    
    private static final String DS = "dataSource.";
    private static final String DR = ".driver";
    private static final String UR = ".url";
    private static final String UN = ".userName";
    private static final String PW = ".password";
    
    private final EbeanManager manager = EbeanManager.DEFAULT;
    
    @Override
    public void onLoad() {
        List<String> enalbes = getConfig().getStringList("enables");
        if (enalbes.size() < 1) {
            enalbes.add("default");
            getConfig().set("enables", enalbes);
        }
        for (String name : enalbes) {
             config(name);
        }
    }
    
    @Override
    public void onEnable() {
        List<String> enalbes = getConfig().getStringList("enables");
        for (String name : enalbes) {
             enable(name);
        }
    }

    private void enable(String in) {
        try {
            EbeanHandler handler = manager.getHandler(in);
            if (!handler.isInitialize()) {
                handler.initialize(getClassLoader());
            }
            getLogger().info("Source " + in + " enable done!");
        } catch (Exception e) {
            getLogger().info("Source " + in + " " + e.getMessage());
        }
    }

    private void config(String name) {
        String driver   = getConfig().getString(DS + name + DR);
        String url      = getConfig().getString(DS + name + UR);
        String userName = getConfig().getString(DS + name + UN);
        String password = getConfig().getString(DS + name + PW);
        
        if (driver != null && url != null && userName != null 
                && password != null) {
            EbeanHandler handler = manager.getHandler(name);
            
            handler.setDriver(driver);
            handler.setUrl(url);
            handler.setUserName(userName);
            handler.setPassword(password);
        } else {
            getConfig().set(DS + name + DR, Default.DRIVER);
            getConfig().set(DS + name + UR, Default.URL);
            getConfig().set(DS + name + UN, Default.USER_NAME);
            getConfig().set(DS + name + PW, Default.PASSWORD);
            
            saveConfig();
        }
    }
    
    public static class Default {

        public static final Object PASSWORD = "testPassword";
        public static final Object USER_NAME = "testUserName";
        public static final Object DRIVER = "com.mysql.jdbc.Driver";
        public static final String URL = "jdbc:mysql://localhost/db";
        
    }

}
