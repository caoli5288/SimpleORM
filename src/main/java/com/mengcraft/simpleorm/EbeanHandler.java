package com.mengcraft.simpleorm;

import java.util.ArrayList;
import java.util.List;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;

public class EbeanHandler {

    private final String name;
    private final List<Class> list;

    private String driver;
    private String url;
    private String userName;
    private String password;

    private boolean initialize;

    private EbeanServer server;

    public EbeanHandler(String name) {
        this.name = name;
        this.list = new ArrayList<Class>();
    }

    public void initialize(ClassLoader in) throws Exception {
        if (isInitialize()) {
            throw new RuntimeException("Already initialize!");
        }
        if (driver == null || url == null) {
            throw new NullPointerException("Not configured!");
        }
        if (userName == null || password == null) {
            throw new NullPointerException("Not configured!");
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

        for (Class<?> line : list) {
            sc.addClass(line);
        }
        ClassLoader loader = Thread.currentThread()
                                   .getContextClassLoader();
        
        Thread.currentThread().setContextClassLoader(in);
        server = EbeanServerFactory.create(sc);
        Thread.currentThread().setContextClassLoader(loader);
        
        initialize = true;
    }

    public String getName() {
        return name;
    }
    
    public void addClass(Class<?> in) {
        if (!list.contains(in)) {
            list.add(in);
        }
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
        if (!isInitialize()) {
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

}
