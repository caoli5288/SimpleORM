package com.mengcraft.simpleorm;

import static java.lang.Thread.currentThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.Query;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;

public class EbeanHandler {

    private String driver = EbeanManager.Default.DRIVER;
    private String url = EbeanManager.Default.URL;
    private String userName = EbeanManager.Default.USER_NAME;
    private String password = EbeanManager.Default.PASSWORD;

    private final Plugin proxy;
    private final ReflectUtil util;
    private final List<Class> list;

    private String name;

    private int coreSize = 1;
    private int maxSize = 4;

    private IsolationLevel isolationLevel;

    private EbeanServer server;

    public EbeanHandler(Plugin proxy) {
        this.proxy = proxy;
        this.util = ReflectUtil.UTIL;
        this.list = new ArrayList<>();
    }

    public EbeanHandler(JavaPlugin proxy) {
        this(Plugin.class.cast(proxy));
    }

    @Override
    public String toString() {
        return getName() + ", " + url + ", " + userName + ", " + (server != null);
    }

    public void define(Class<?> in) {
        if (server != null) {
            throw new RuntimeException("Already initialize!");
        }
        if (!list.contains(in)) list.add(in);
    }

    public <T> Query<T> find(Class<T> in) {
        return getServer().find(in);
    }

    public <T> T find(Class<T> in, Object id) {
        return getServer().find(in, id);
    }

    public void reflect() {
        if (server == null) {
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
        if (server == null) {
            throw new RuntimeException("Not initialize!");
        }
        try {
            SpiEbeanServer serv = (SpiEbeanServer) server;
            DdlGenerator gen = serv.getDdlGenerator();
            gen.runScript(true, gen.generateDropDdl());
        } catch (Exception e) {
            proxy.getLogger().info(e.getMessage());
        }
    }

    /**
     * Create defined classes's tables.
     *
     * @param ignore Ignore exception when run create table.
     */
    public void install(boolean ignore) {
        if (server == null) {
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
            DdlGenerator gen = ((SpiEbeanServer) server).getDdlGenerator();
            gen.runScript(ignore, gen.generateCreateDdl());
            proxy.getLogger().info("Create Tables done!");
        }
    }

    public void install() {
        install(false);
    }

    public void initialize(String name) throws Exception {
        if (server != null) {
            throw new RuntimeException("Already initialize!");
        } else if (list.size() < 1) {
            throw new RuntimeException("Not define entity class!");
        }
        // Initialize handler name.
        setName(name);

        DataSourceConfig sourceConfig = new DataSourceConfig();

        sourceConfig.setDriver(driver);
        sourceConfig.setUrl(url);
        sourceConfig.setUsername(userName);
        sourceConfig.setPassword(password);

        sourceConfig.setMinConnections(coreSize);
        sourceConfig.setMaxConnections(maxSize);

        ServerConfig serverConfig = new ServerConfig();

        if (this.isolationLevel != null) {
            sourceConfig.setIsolationLevel(this.isolationLevel.getRawLevel());
        }

        if (driver.contains("sqlite")) {
            // Rewrite isolation level if is SQLite platform.
            sourceConfig.setIsolationLevel(8);
            serverConfig.setDatabasePlatform(new SQLitePlatform());
            serverConfig.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        }

        serverConfig.setName(name);
        serverConfig.setDataSourceConfig(sourceConfig);

        for (Class<?> line : list) {
            serverConfig.addClass(line);
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        currentThread().setContextClassLoader(util.loader(proxy));
        setServer(EbeanServerFactory.create(serverConfig));
        currentThread().setContextClassLoader(loader);
    }

    public void initialize() throws Exception {
        initialize(getProxy().getName());
    }

    public void save(Object in) {
        getServer().save(in);
    }

    public int save(Collection<?> collection) {
        return getServer().save(collection);
    }

    public void insert(Object record) {
        getServer().insert(record);
    }

    public void update(Object record) {
        getServer().update(record);
    }

    public void delete(Object record) {
        getServer().delete(record);
    }

    public int delete(Collection<?> records) {
        return getServer().delete(records);
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
        if (server == null) {
            throw new RuntimeException("Not initialize!");
        }
        return server;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public boolean isInitialized() {
        return server != null;
    }

    public boolean isNotInitialized() {
        return server == null;
    }

    public Plugin getProxy() {
        return proxy;
    }

    public String getName() {
        return name;
    }

    public <T> T bean(Class<T> in) {
        return getServer().createEntityBean(in);
    }

    private void setName(String name) {
        if (name != null) {
            this.name = name;
        } else throw new NullPointerException();
    }

    public int getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    private void setServer(EbeanServer server) {
        this.server = server;
    }

    public void setIsolationLevel(IsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public IsolationLevel getIsolationLevel() {
        return this.isolationLevel;
    }

}
