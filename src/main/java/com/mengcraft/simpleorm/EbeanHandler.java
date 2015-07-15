package com.mengcraft.simpleorm;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;

public class EbeanHandler {

    private final EbeanManager manager = EbeanManager.DEFAULT;
    
    private final ReflectUtil util;
    private final List<Class> list;
    
    private String name;
    
    private String driver;
    private String url;
    private String userName;
    private String password;

    private boolean initialized;

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
        return name + "," + url + "," + userName + "," + initialized;
    }
    
    public void define(Class<?> in) {
        if (!initialized && !list.contains(in)) {
            list.add(in);
        }
    }
    
    public void reflect() {
        if (!initialized) {
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
    
    public void uninstall() {
        if (!initialized) {
            throw new RuntimeException("Not initialize!");
        }
        try {
            SpiEbeanServer serv = (SpiEbeanServer) server;
            DdlGenerator gen = serv.getDdlGenerator();
            gen.runScript(false, gen.generateDropDdl());
        } catch (Exception e) {
            proxy.getLogger().info(e.getMessage());
        }
    }
    
    public void install() {
        if (!initialized) {
            throw new RuntimeException("Not initialize!");
        }
        try {
            for (Class<?> line : list) {
                server.find(line).findRowCount();
            }
            proxy.getLogger().info("Tables already exists!");
        } catch (Exception e) {
            proxy.getLogger().info(e.getMessage());
            proxy.getLogger().info("Start create tables, wait...");
            SpiEbeanServer serv = (SpiEbeanServer) server;
            DdlGenerator gen = serv.getDdlGenerator();
            gen.runScript(false, gen.generateCreateDdl());
            proxy.getLogger().info("Create Tables done!");
        }
    }

    public void initialize() throws Exception {
        if (initialized) {
            throw new RuntimeException("Already initialize!");
        } else if (driver == null || url == null || userName == null
                || password == null) {
            throw new NullPointerException("Not configured!");
        } else if (list.size() < 1) {
            throw new RuntimeException("Not define entity class!");
        }
        DataSourceConfig dsc = new DataSourceConfig();
        
        dsc.setDriver(driver);
        dsc.setUrl(url);
        dsc.setUsername(userName);
        dsc.setPassword(password);
        
        ServerConfig sc = new ServerConfig();
        
        if (driver.contains("sqlite")) {
            dsc.setIsolationLevel(8);
            sc.setDatabasePlatform(new SQLitePlatform());
            sc.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        }
        
        sc.setName(name);
        sc.setDataSourceConfig(dsc);
        
        for (Class<?> line : list) {
            sc.addClass(line);
        }
        
        ClassLoader loader = Thread.currentThread()
                                   .getContextClassLoader();
        
        Thread.currentThread().setContextClassLoader(util.loader(proxy));
        server = EbeanServerFactory.create(sc);
        Thread.currentThread().setContextClassLoader(loader);
        

        initialized = true;
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
        if (!initialized) {
            throw new NullPointerException("Not initialized!");
        }
        return server;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public JavaPlugin getProxy() {
        return proxy;
    }

    public void setProxy(JavaPlugin in) {
        if (this.proxy != in) {
            this.proxy = in;
        }
    }

}
