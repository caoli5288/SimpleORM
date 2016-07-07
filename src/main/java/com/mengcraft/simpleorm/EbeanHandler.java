package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.Query;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.lang.Thread.currentThread;

public class EbeanHandler {

    private final Set<Class> typeSet = new HashSet<>();
    private final Plugin proxy;

    private String heartbeat = "select 1";
    private String name;
    private String driver;
    private String url;
    private String userName;
    private String password;

    private int coreSize = 1;
    private int maxSize = (Runtime.getRuntime().availableProcessors() << 1) + 1;

    private IsolationLevel isolationLevel;
    private EbeanServer server;

    public EbeanHandler(Plugin proxy) {
        this.proxy = proxy;
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
            throw new NullPointerException("Already initialized!");
        }
        typeSet.add(in);
    }

    public <T> Query<T> find(Class<T> in) {
        return getServer().find(in);
    }

    public <T> T find(Class<T> in, Object id) {
        return getServer().find(in, id);
    }

    public void reflect() {
        if (server == null) {
            throw new NullPointerException("Not initialized!");
        }
        if (!proxy.getDescription().isDatabaseEnabled()) { // hacked 1.9.2
            proxy.getDescription().setDatabaseEnabled(true);
        }
        if (proxy.getDatabase() != server) {
            try {
                Reflect.replace(proxy, server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void uninstall() {
        if (server == null) {
            throw new NullPointerException("Not initialized!");
        }
        try {
            SpiEbeanServer spi = SpiEbeanServer.class.cast(server);
            DdlGenerator gen = spi.getDdlGenerator();
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
            throw new NullPointerException("Not initialized!");
        }
        try {
            for (Class<?> line : typeSet) {
                server.find(line).setMaxRows(1).findUnique();
            }
            proxy.getLogger().info("Tables already exists!");
        } catch (Exception e) {
            proxy.getLogger().info(e.getMessage());
            proxy.getLogger().info("Start create tables, wait...");
            DdlGenerator gen = SpiEbeanServer.class.cast(server).getDdlGenerator();
            gen.runScript(ignore, gen.generateCreateDdl());
            proxy.getLogger().info("Create tables done!");
        }
    }

    public void install() {
        install(false);
    }

    public void initialize(String name) throws DatabaseException {
        if (server != null) {
            throw new DatabaseException("Already initialized!");
        } else if (typeSet.size() < 1) {
            throw new DatabaseException("Not define entity class!");
        }
        // Initialize handler name.
        setName(name);

        DataSourceConfig sourceConfig = new DataSourceConfig();

        sourceConfig.setHeartbeatSql(heartbeat);
        sourceConfig.setDriver(driver);
        sourceConfig.setUrl(url);
        sourceConfig.setUsername(userName);
        sourceConfig.setPassword(password);

        sourceConfig.setMinConnections(coreSize);
        sourceConfig.setMaxConnections(maxSize);


        if (this.isolationLevel != null) {
            sourceConfig.setIsolationLevel(this.isolationLevel.getRawLevel());
        }

        ServerConfig serverConfig = new ServerConfig();

        if (driver.contains("sqlite")) {
            // Rewrite isolation level if is SQLite platform.
            sourceConfig.setIsolationLevel(8);
            serverConfig.setDatabasePlatform(new SQLitePlatform());
            serverConfig.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        }

        serverConfig.setName(name);
        serverConfig.setDataSourceConfig(sourceConfig);

        for (Class<?> type : typeSet) {
            serverConfig.addClass(type);
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            currentThread().setContextClassLoader(Reflect.getLoader(proxy));
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
        setServer(EbeanServerFactory.create(serverConfig));
        currentThread().setContextClassLoader(loader);
    }

    public void initialize() throws DatabaseException {
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
            throw new NullPointerException("Not initialized!");
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
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
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

    /**
     * Set heartbeat sql command for this handler. Set it only if the
     * default value "select 1" not compatible with your data-source.
     *
     * @param heartbeat The heartbeat sql command
     */
    public void setHeartbeat(String heartbeat) {
        this.heartbeat = heartbeat;
    }

    public void setIsolationLevel(IsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public IsolationLevel getIsolationLevel() {
        return this.isolationLevel;
    }

}
