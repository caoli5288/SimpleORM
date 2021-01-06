package com.mengcraft.simpleorm.serializable;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Constructor;
import java.util.Map;

@AllArgsConstructor
public class ConstructorDeserializer<T> implements IDeserializer<T> {

    private final Constructor<?> constructor;

    @Override
    @SneakyThrows
    public T deserialize(Class<T> cls, Map<String, ?> map) {
        return (T) constructor.newInstance(map);
    }
}
