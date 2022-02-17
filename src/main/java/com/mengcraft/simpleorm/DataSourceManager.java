package com.mengcraft.simpleorm;

import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.mengcraft.simpleorm.lib.Utils;
import com.mengcraft.simpleorm.provider.IDataSourceProvider;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import org.bukkit.configuration.file.FileConfiguration;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@ExtensionMethod(Utils.class)
public class DataSourceManager implements IDataSourceProvider {

    private final Map<String, DataSource> poolMap = Maps.newHashMap();

    @Override
    public DataSource getDataSource(EbeanHandler handler) {
        // check exists
        if (handler.getDataSource() != null) {
            return handler.getDataSource();
        }
        // check build-in options
        if (!handler.getOptions().getJdbcUrl().isNullOrEmpty()) {
            return handler.newDataSource();
        }
        // check explicit
        DataSource ds = poolMap.get(handler.getPlugin().getName());
        if (ds == null) {
            // fallback to default
            ds = poolMap.get("default");
        }
        return ds;
    }

    @SneakyThrows
    public void load(ORM orm) {
        // save default config
        orm.saveResource("datasources.d/default.yml", false);
        // check compatible first
        FileConfiguration ormConfig = orm.getConfig();
        String dsu = ormConfig.getString("dataSource.url");
        if (!dsu.isNullOrEmpty()) {
            DataSourceOptions def = new DataSourceOptions();
            def.setJdbcUrl(dsu);
            def.setUsername(ormConfig.getString("dataSource.user"));
            def.setPassword(ormConfig.getString("dataSource.password"));
            def.setMaximumPoolSize(10);
            Utils.YAML.dump(ORM.serialize(def), Files.newWriter(new File(orm.getDataFolder(), "datasources.d/default.yml"), StandardCharsets.UTF_8));
            ormConfig.set("dataSource", null);
            orm.saveConfig();
        }
        // loads
        File[] files = new File(orm.getDataFolder(), "datasources.d").listFiles();
        Objects.requireNonNull(files);// ?
        for (File f : files) {
            if (Files.getFileExtension(f.getName()).equals("yml")) {// load it
                load(f);
            }
        }
    }

    @SneakyThrows
    private void load(File f) {
        Map<String, Object> map = Utils.YAML.load(Files.newReader(f, StandardCharsets.UTF_8));
        DataSourceOptions options = ORM.deserialize(DataSourceOptions.class, map);
        load(Files.getNameWithoutExtension(f.getName()), options);
    }

    public void load(String poolName, DataSourceOptions options) {
        HikariDataSource ds = options.asDataSource(poolName);
        if (!options.getAliases().isNullOrEmpty()) {
            for (String alias : options.getAliases()) {
                poolMap.put(alias, ds);
            }
        }
        poolMap.put(poolName, ds);
    }
}
