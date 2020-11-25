package com.mengcraft.simpleorm.driver;

public class MySqlDriver extends IDatabaseDriver {

    @Override
    protected String clazz() {
        return "com.mysql.jdbc.Driver";
    }

    @Override
    protected String protocol() {
        return "mysql";
    }

    @Override
    protected String description() {
        return "mysql:mysql-connector-java:jar:5.1.49";
    }
}
