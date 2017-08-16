package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.Query;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.core.DefaultServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;
import com.zaxxer.hikari.HikariDataSource;
import lombok.EqualsAndHashCode;
import lombok.val;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

@EqualsAndHashCode(of = "id")
public class EbeanHandler {

    private final Set<Class> mapping = new HashSet<>();
    private final JavaPlugin plugin;
    private final boolean managed;
    private final UUID id = UUID.randomUUID();

    private HikariDataSource pool;
    private String heartbeat;
    private String name;
    private String driver;
    private String url;
    private String userName;
    private String password;

    private int coreSize = 1;
    private int maxSize = (Runtime.getRuntime().availableProcessors() << 1) + 1;

    private IsolationLevel isolationLevel;
    private EbeanServer server;

    EbeanHandler(JavaPlugin plugin, boolean managed) {
        name = plugin.getName() + '@' + id;
        this.plugin = plugin;
        this.managed = managed;
    }

    public EbeanHandler(JavaPlugin plugin) {
        this(plugin, false);
    }

    @Override
    public String toString() {
        return "ORM(" + name + ", " + url + ", " + userName + ", ready = " + !(server == null) + ")";
    }

    public void define(Class<?> in) {
        if (server != null) {
            throw new NullPointerException("Already initialized!");
        }
        mapping.add(in);
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
        try {
            PluginDescriptionFile desc = plugin.getDescription();
            if (!((boolean) desc.getClass().getMethod("isDatabaseEnabled").invoke(desc))) {
                desc.getClass().getMethod("setDatabaseEnabled", boolean.class).invoke(desc, true);
            }
            Reflect.replace(plugin, server);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "注入失败了，1.12服务端不再支持注入方式|" + e.toString());
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
            plugin.getLogger().info(e.getMessage());
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
            for (Class<?> line : mapping) {
                server.find(line).setMaxRows(1).findUnique();
            }
            plugin.getLogger().info("Tables already exists!");
        } catch (Exception e) {
            plugin.getLogger().info(e.getMessage());
            plugin.getLogger().info("Start create tables, wait...");
            DdlGenerator gen = SpiEbeanServer.class.cast(server).getDdlGenerator();
            gen.runScript(ignore, gen.generateCreateDdl());
            plugin.getLogger().info("Create tables done!");
        }
    }

    public void install() {
        install(false);
    }

    public void initialize() throws DatabaseException {
        if (!(server == null)) {
            throw new DatabaseException("Already initialized!");
        }
        if (mapping.size() < 1) {
            throw new DatabaseException("Not define entity class!");
        }
        if (!(pool == null)) {
            throw new DatabaseException("Already shutdown!");
        }
        // Hacked in newest modded server
        PolicyInjector.inject();

        pool = new HikariDataSource();

        pool.setPoolName(name);

        pool.setConnectionTimeout(10_000);
        pool.setJdbcUrl(url);
        pool.setUsername(userName);
        pool.setPassword(password);

        pool.setAutoCommit(false);
        pool.setMinimumIdle(coreSize);
        pool.setMaximumPoolSize(maxSize);

        val conf = new ServerConfig();

        if (url.startsWith("jdbc:sqlite:")) {
            // Fix ebean-2.7(at bukkit-1.7.10) compatible
            pool.setConnectionTestQuery("select 1");
            pool.setDriverClassName("org.sqlite.JDBC");
            //
            pool.setTransactionIsolation("TRANSACTION_SERIALIZABLE");
            conf.setDatabasePlatform(new SQLitePlatform());
            conf.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        } else {
            if (!(driver == null)) {
                pool.setDriverClassName(driver);
            }
            if (!(heartbeat == null)) {
                pool.setConnectionTestQuery(heartbeat);
            }
            if (!(isolationLevel == null)) {
                pool.setTransactionIsolation("TRANSACTION_" + isolationLevel.name());
            }

        }

        conf.setName(name);
        conf.setDataSource(pool);

        for (Class<?> type : mapping) {
            conf.addClass(type);
        }

        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Reflect.getLoader(plugin));
            server = EbeanServerFactory.create(conf);
        } catch (Exception e) {
            throw new DatabaseException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(ctx);
        }
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

    @Deprecated
    public void setDriver(String driver) {
        this.driver = driver;
    }

    public boolean isInitialized() {
        return server != null;
    }

    public boolean isNotInitialized() {
        return server == null;
    }

    public Plugin getPlugin() {
        return plugin;
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

    @Deprecated
    public void setHeartbeat(String heartbeat) {
        this.heartbeat = heartbeat;
    }

    /**
     * @deprecated not very recommend to call this method manual, it will shutdown automatic while JVM down
     */
    @Deprecated
    public void shutdown() throws DatabaseException {
        try {
            if (server == null) throw new DatabaseException("Not initialized!");
            val clz = Class.forName("com.avaje.ebeaninternal.server.core.DefaultServer$Shutdown");
            val i = clz.getDeclaredConstructor(DefaultServer.class);
            i.setAccessible(true);
            ((Runnable) i.newInstance(server)).run();
            pool.close();
            if (managed) {
                EbeanManager.shutdown(this);
            }
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }

    public void setIsolationLevel(IsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public IsolationLevel getIsolationLevel() {
        return this.isolationLevel;
    }

}
