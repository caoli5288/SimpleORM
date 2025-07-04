package com.mengcraft.simpleorm.driver;

import com.mengcraft.simpleorm.lib.MavenLibs;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class IDatabaseDriver {

    protected abstract String clazz();

    protected abstract String protocol();

    protected abstract String description();

    void load() {
        try {
            Class.forName(clazz());
        } catch (ClassNotFoundException e) {
            MavenLibs.of(description()).load();
            load();
        }
    }
}
