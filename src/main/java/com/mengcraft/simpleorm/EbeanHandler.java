package com.mengcraft.simpleorm;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;

public class EbeanHandler {

    private final ReflectUtil util;
    private final List<Class> list;
    
    private String name;
    
    private String driver;
    private String url;
    private String userName;
    private String password;

    private boolean initialize;

    private EbeanServer server;
    private JavaPlugin proxy;

    public EbeanHandler(JavaPlugin proxy) {
        this.proxy = proxy;
        this.name = proxy.getName();
        this.util = ReflectUtil.UTIL;
        this.list = new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return name + "," + url + "," + userName + "," + initialize;
    }
    
    public void define(Class in) {
        if (!initialize && !list.contains(in)) {
            list.add(in);
        }
    }
    
    public void register() {
        if (!initialize) {
            throw new RuntimeException("Not initialize!");
        }
        if (proxy.getDatabase() != server) {
            try {
                util.register(proxy, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void initialize() throws Exception {
        if (initialize) {
            throw new RuntimeException("Already initialize!");
        }
        if (driver == null || url == null || userName == null 
                || password == null) {
            throw new NullPointerException("Not configured!");
        }
        if (list.size() < 1) {
            throw new RuntimeException("Not define entity class!");
        }
        DataSourceConfig dsc = new DataSourceConfig();
        
        dsc.setDriver(driver);
        dsc.setUrl(url);
        dsc.setUsername(userName);
        dsc.setPassword(password);

        ServerConfig sc = new ServerConfig();
        sc.setName(name);
        sc.setDataSourceConfig(dsc);

        sc.setDdlGenerate(true);
        sc.setDdlRun(true);
        
        ClassLoader loader = Thread.currentThread()
                                 .getContextClassLoader();
        
        Thread.currentThread().setContextClassLoader(util.loader(proxy));
        server = EbeanServerFactory.create(sc);
        Thread.currentThread().setContextClassLoader(loader);

        initialize = true;
    }

    public String getName() {
        return name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public EbeanServer getServer() {
        if (!initialize) {
            throw new NullPointerException("Not initialized!");
        }
        return server;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public boolean isInitialize() {
        return initialize;
    }

    public JavaPlugin getProxy() {
        return proxy;
    }

    public EbeanHandler setProxy(JavaPlugin in) {
        this.proxy = in;
        return this;
    }

}
