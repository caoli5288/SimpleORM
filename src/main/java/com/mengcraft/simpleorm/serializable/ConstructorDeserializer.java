package com.mengcraft.simpleorm.serializable;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Constructor;
import java.util.Map;

@AllArgsConstructor
public class ConstructorDeserializer implements IDeserializer {

    private final Constructor<?> constructor;

    @Override
    @SneakyThrows
    public Object deserialize(Class<?> cls, Map<String, Object> map) {
        return constructor.newInstance(map);
    }
}
