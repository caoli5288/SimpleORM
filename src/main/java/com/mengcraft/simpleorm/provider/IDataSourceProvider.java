package com.mengcraft.simpleorm.provider;

import com.mengcraft.simpleorm.EbeanHandler;

import javax.sql.DataSource;

public interface IDataSourceProvider {

    DataSource getDataSource(EbeanHandler handler);
}
