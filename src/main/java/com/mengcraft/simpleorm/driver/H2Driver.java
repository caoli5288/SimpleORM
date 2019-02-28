package com.mengcraft.simpleorm.driver;

public class H2Driver extends IDatabaseDriver {

    @Override
    protected String clazz() {
        return "org.h2.Driver";
    }

    @Override
    protected String protocol() {
        return "h2";
    }

    @Override
    protected String description() {
        return "com.h2database:h2:1.4.198";
    }
}
