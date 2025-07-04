package com.mengcraft.simpleorm;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mengcraft.simpleorm.driver.DatabaseDriverRegistry;
import com.mengcraft.simpleorm.driver.IDatabaseDriver;
import com.mengcraft.simpleorm.lib.Utils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.experimental.ExtensionMethod;

import java.util.List;
import java.util.Map;

@Data
@ExtensionMethod(Utils.class)
public class DataSourceOptions {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driver;
    private int maximumPoolSize;
    private List<String> aliases = Lists.newArrayList();
    private Map<String, String> properties = Maps.newHashMap();

    public HikariDataSource asDataSource(String poolName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(poolName);
        ds.setJdbcUrl(DatabaseDriverRegistry.filter(jdbcUrl));
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMinimumIdle(1);
        ds.setMaximumPoolSize(Math.max(8, maximumPoolSize));
        ds.setAutoCommit(false);
        ds.setConnectionTimeout(3000);
        if (!driver.isNullOrEmpty()) {
            ds.setDriverClassName(driver);
        }
        if (!properties.isNullOrEmpty()) {
            properties.forEach(ds::addDataSourceProperty);
        }
        return ds;
    }
}
