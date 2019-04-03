package com.mengcraft.simpleorm.lib;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class TypeFunctionRegistry<R> {

    private final Map<Class<?>, Function<Object, R>> all;

    public TypeFunctionRegistry() {
        this(new HashMap<>());
    }

    public TypeFunctionRegistry(Map<Class<?>, Function<Object, R>> all) {
        this.all = all;
    }

    @SuppressWarnings("unchecked")
    public <T> void register(Class<T> key, Function<T, R> function) {
        all.put(key, (Function<Object, R>) function);
    }

    public R handle(Object input) {
        Class<?> clz = input.getClass();
        if (all.containsKey(clz)) {
            return all.get(clz).apply(input);
        }
        return null;
    }

    public Set<Class<?>> getKeys() {
        return all.keySet();
    }
}
