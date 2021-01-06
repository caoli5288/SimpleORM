package com.mengcraft.simpleorm.serializable;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Method;
import java.util.Map;

@AllArgsConstructor
public class MethodDeserializer<T> implements IDeserializer<T> {

    private final Method method;

    @Override
    @SneakyThrows
    public T deserialize(Class<T> cls, Map<String, ?> map) {
        return (T) method.invoke(cls, map);
    }
}
