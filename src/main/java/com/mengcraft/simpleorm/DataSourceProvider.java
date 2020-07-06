package com.mengcraft.simpleorm;

import com.mengcraft.simpleorm.provider.IDataSourceProvider;

import javax.sql.DataSource;

public class DataSourceProvider implements IDataSourceProvider {

    @Override
    public DataSource getDataSource(EbeanHandler handler) {
        return handler.newDataSource();
    }
}
