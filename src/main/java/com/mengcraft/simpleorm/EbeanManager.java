package com.mengcraft.simpleorm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import com.mengcraft.simpleorm.Main.Default;

public class EbeanManager {

    public static final EbeanManager DEFAULT = new EbeanManager();

    private final Map<String, EbeanHandler> map;

    private EbeanManager() {
        this.map = new HashMap<>();
    }

    public Collection<EbeanHandler> handers() {
        return map.values();
    }

    public EbeanHandler getHandler(JavaPlugin proxy) {
        String name = proxy.getName();
        return map.get(name) != null ?
               map.get(name).setProxy(proxy) :
               load(proxy);
    }

    private EbeanHandler load(JavaPlugin proxy) {
        EbeanHandler out = new EbeanHandler(proxy);
        
        String driver = proxy.getConfig()
                .getString("dataSource.driver");
        String url = proxy.getConfig()
                .getString("dataSource.url");
        String userName = proxy.getConfig()
                .getString("dataSource.userName");
        String password = proxy.getConfig()
                .getString("dataSource.password");
        
        if (driver != null) {
            out.setDriver(driver);
        } else {
            proxy.getConfig().set("dataSource.driver"
                    , Default.DRIVER);
            proxy.saveConfig();
        }
        
        if (url != null) {
            out.setUrl(url);
        } else {
            proxy.getConfig().set("dataSource.url"
                    , Default.URL);
            proxy.saveConfig();
        }
        
        if (userName != null) {
            out.setUserName(userName);
        } else {
            proxy.getConfig().set("dataSource.userName"
                    , Default.USER_NAME);
            proxy.saveConfig();
        }
        
        if (password != null) {
            out.setPassword(password);
        } else {
            proxy.getConfig().set("dataSource.password"
                    , Default.PASSWORD);
            proxy.saveConfig();
        }
        
        map.put(proxy.getName(), out);
        
        return out;
    }

}
