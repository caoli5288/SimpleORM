package com.mengcraft.simpleorm.lib;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class FunctionRegistry<T, R> {

    private final Map<Object, Function<T, R>> all;

    public FunctionRegistry(Map<Object, Function<T, R>> all) {
        this.all = all;
    }

    public FunctionRegistry() {
        all = new HashMap<>();
    }

    public void register(Object key, Function<T, R> function) {
        all.put(key, function);
    }

    public R handle(Object key, T input) {
        if (all.containsKey(key)) {
            return all.get(key).apply(input);
        }
        return null;
    }

    public Set<Object> getKeys() {
        return all.keySet();
    }
}
