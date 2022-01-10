package com.mengcraft.simpleorm;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.LogLevel;
import com.avaje.ebean.Query;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.dbplatform.SQLitePlatform;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.core.DefaultServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;
import com.google.common.base.Preconditions;
import com.mengcraft.simpleorm.annotation.Index;
import com.mengcraft.simpleorm.lib.Utils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.ExtensionMethod;
import lombok.val;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import javax.persistence.Entity;
import javax.sql.DataSource;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

@EqualsAndHashCode(of = "id")
@ExtensionMethod(Utils.class)
public class EbeanHandler {

    private final Set<Class<?>> mapping = new HashSet<>();
    private final JavaPlugin plugin;
    private final boolean managed;
    private final UUID id = UUID.randomUUID();
    @Getter
    private final DataSourceOptions options = new DataSourceOptions();
    private DataSource dataSource;
    private String heartbeat;
    private String name;
    private LogLevel loggingLevel = LogLevel.NONE;

    private int coreSize;
    private int maxSize = ORM.getMaximumSize();

    private IsolationLevel isolationLevel;
    private EbeanServer server;

    EbeanHandler(JavaPlugin plugin, boolean managed, DataSource dataSource) {
        name = plugin.getName() + '@' + id;
        this.plugin = plugin;
        this.managed = managed;
        this.dataSource = dataSource;
    }

    public EbeanHandler(JavaPlugin plugin) {
        this(plugin, false, null);
    }

    @Override
    public String toString() {
        return "ORM(" + name + ", " + options + ", ready = " + !(server == null) + ")";
    }

    /**
     * Plz remember close connection(s).
     *
     * @return pooled jdbc connection
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException {
        validInitialized();
        return dataSource.getConnection();
    }

    /**
     * @deprecated Bukkit-1.12 deprecated {@code Plugin.getDatabase()}.
     */
    public void reflect() {
        validInitialized();
        if (!managed) {
            plugin.getLogger().warning("自定义连接不能注入到端");
            return;
        }
        if (Reflect.isLegacy()) {
            PluginDescriptionFile desc = plugin.getDescription();
            Reflect.setDatabaseEnabled(desc, true);
            Reflect.setEbeanServer(plugin, server);
        } else {
            plugin.getLogger().log(Level.WARNING, "注入失败了，1.12服务端不再支持注入方式");
        }
    }

    @Deprecated
    public void define(Class<?> clz) {
        add(clz);
    }

    public void add(Class<?> clz) {
        if (isInitialized()) {
            throw new IllegalStateException("Already initialized!");
        }
        Entity annotation = clz.getAnnotation(Entity.class);
        if (annotation == null) {
            plugin.getLogger().warning(String.format("Exception occurred while register entity class. %s not annotated by @Entity", clz.getName()));
        }
        mapping.add(clz);
    }

    public <T> Query<T> find(Class<T> in) {
        return getServer().find(in);
    }

    public <T> T find(Class<T> in, Object id) {
        return getServer().find(in, id);
    }

    public void uninstall() {
        validInitialized();
        try {
            SpiEbeanServer spi = (SpiEbeanServer) server;
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
    public void install(boolean ignore, Runnable postprocessor) {
        validInitialized();
        try {
            for (Class<?> line : mapping) {
                server.find(line).setMaxRows(1).findList();
            }
            plugin.getLogger().info("Tables already exists!");
        } catch (Exception e) {
            plugin.getLogger().info(e.getMessage());
            plugin.getLogger().info("Start create tables, wait...");
            DdlGenerator gen = ((SpiEbeanServer) server).getDdlGenerator();
            gen.runScript(ignore, gen.generateCreateDdl());
            plugin.getLogger().info("Create tables done!");
            for (Class<?> cls : mapping) {
                indexesGenerator(cls);
            }
            if (postprocessor != null) {
                postprocessor.run();
                plugin.getLogger().info("Execute postprocessor done!");
            }
        }
    }

    private void indexesGenerator(Class<?> cls) {
        Index[] definitions = cls.getAnnotationsByType(Index.class);
        if (definitions.length == 0) {
            return;
        }
        String clsName = Utils.translateSqlName(cls).toLowerCase();
        int count = 0;
        String createSql = "CREATE %s %s ON %s (%s)";
        for (Index index : definitions) {
            String[] columnNames = index.value();
            if (columnNames.length != 0) {
                String name = index.name();
                if (Utils.isNullOrEmpty(name)) {
                    name = "auto_index" + count++;
                }
                String sql = String.format(createSql, index.unique() ? "UNIQUE INDEX" : "INDEX", name, clsName, String.join(", ", columnNames));
                plugin.getLogger().info("execute indexes sql " + sql);
                server.createSqlUpdate(sql).execute();
            }
        }
    }

    public void install(boolean ignore) {
        install(ignore, null);
    }

    public void install() {
        install(false);
    }

    /**
     * @deprecated Internal only
     */
    DataSource newDataSource() {
        HikariDataSource ds = options.asDataSource(name);
        ds.setMinimumIdle(Math.max(1, coreSize));
        ds.setMaximumPoolSize(maxSize);
        if (!Utils.isNullOrEmpty(heartbeat)) {
            ds.setConnectionTestQuery(heartbeat);
        }
        if (isolationLevel != null) {
            ds.setTransactionIsolation("TRANSACTION_" + isolationLevel.name());
        }
        return ds;
    }

    public void initialize() throws DatabaseException {
        if (!(server == null)) {
            throw new DatabaseException("Already initialized!");
        }
        if (mapping.size() < 1) {
            throw new DatabaseException("Not define entity class!");
        }

        PolicyInjector.inject();// Hacked in forge server

        if (dataSource == null) {
            dataSource = ORM.getDataSourceProvider().getDataSource(this);
        }

        ServerConfig conf = new ServerConfig();
        conf.setName(name);
        conf.setDataSource(dataSource);
        conf.setLoggingLevel(loggingLevel);
        conf.setLoggingToJavaLogger(true);
        if (dataSource instanceof HikariDataSource && ((HikariDataSource) dataSource).getJdbcUrl().startsWith("jdbc:sqlite:")) {
            conf.setDatabasePlatform(new SQLitePlatform());
            conf.getDatabasePlatform().getDbDdlSyntax().setIdentity("");
        }
        if (plugin.getDataFolder().isDirectory() || plugin.getDataFolder().mkdir()) {
            conf.setResourceDirectory(plugin.getDataFolder().toString());
        }

        for (Class<?> type : mapping) {
            conf.addClass(type);
        }

        ClassLoader mainCtx = Thread.currentThread().getContextClassLoader();
        try {
            URLClassLoader cl = (URLClassLoader) Reflect.getLoader(plugin);
            mapping.forEach(clz -> {
                for (URL jar : ((URLClassLoader) clz.getClassLoader()).getURLs()) {
                    Utils.addUrl(cl, jar);
                }
            });
            Thread.currentThread().setContextClassLoader(cl);
            server = EbeanServerFactory.create(conf);
        } catch (Exception e) {
            throw new DatabaseException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(mainCtx);
        }
    }

    public void setJdbcProperty(String key, String value) {
        Preconditions.checkState(isNotInitialized(), "Set property after initialized");
        options.getProperties().put(key, value);
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
        options.setJdbcUrl(url);
    }

    /**
     * @deprecated {@code setUser}
     */
    public void setUserName(String user) {
        options.setUsername(user);
    }

    public void setUser(String user) {
        options.setUsername(user);
    }

    public void setPassword(String password) {
        options.setPassword(password);
    }

    public EbeanServer getServer() {
        validInitialized();
        return server;
    }

    @Deprecated
    public void setDriver(String driver) {
        options.setDriver(driver);
    }

    public boolean isInitialized() {
        return !isNotInitialized();
    }

    public boolean isNotInitialized() {
        return server == null;
    }

    public void validInitialized() {
        if (isNotInitialized()) throw new IllegalStateException("Not initialized!");
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

    private void setName(@NonNull String name) {
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

    /**
     * @param heartbeat the heartbeat sql
     * @deprecated hikari-cp will heartbeat automatic
     */
    public void setHeartbeat(String heartbeat) {
        this.heartbeat = heartbeat;
    }

    public DataSource getDataSource() {
        Preconditions.checkNotNull(dataSource, "dataSource not initialized");
        return dataSource;
    }

    /**
     * @deprecated not very recommend to call this method manual, it will shutdown automatic while JVM down
     */
    @Deprecated
    public void shutdown() throws DatabaseException {
        validInitialized();
        try {
            val clz = Class.forName("com.avaje.ebeaninternal.server.core.DefaultServer$Shutdown");
            val i = clz.getDeclaredConstructor(DefaultServer.class);
            i.setAccessible(true);
            ((Runnable) i.newInstance(server)).run();
            if (dataSource instanceof HikariDataSource && !((HikariDataSource) dataSource).getPoolName().equals("simple_shared")) {// Never shutdown shared
                ((HikariDataSource) dataSource).close();
            }
            if (managed) {
                EbeanManager.unHandle(this);
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

    public LogLevel getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(LogLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    public static EbeanHandler build(@NonNull JavaPlugin plugin, @NonNull Map<String, String> map) {
        val out = new EbeanHandler(plugin);
        out.setUrl(map.get("url"));
        out.setUser(map.getOrDefault("userName", map.get("username")));
        out.setPassword(map.get("password"));
        if (map.containsKey("driver")) {
            out.setDriver(map.get("driver"));
        }
        return out;
    }

}
