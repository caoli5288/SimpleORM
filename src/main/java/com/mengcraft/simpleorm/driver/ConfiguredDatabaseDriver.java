package com.mengcraft.simpleorm.driver;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ConfiguredDatabaseDriver extends IDatabaseDriver {

    private final String tag;
    private final DatabaseDriverInfo info;

    @Override
    protected String clazz() {
        return info.getDriver();
    }

    @Override
    protected String protocol() {
        return tag;
    }

    @Override
    protected String description() {
        return info.getLibrary();
    }
}
