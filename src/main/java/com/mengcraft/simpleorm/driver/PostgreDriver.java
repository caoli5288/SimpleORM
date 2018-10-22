package com.mengcraft.simpleorm.driver;

public class PostgreDriver extends IDatabaseDriver {

    @Override
    protected String clazz() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String protocol() {
        return "postgresql";
    }

    @Override
    protected String description() {
        return "org.postgresql:postgresql:42.2.5";
    }

}
