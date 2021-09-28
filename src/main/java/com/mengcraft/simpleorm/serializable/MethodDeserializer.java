package com.mengcraft.simpleorm.serializable;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.reflect.Method;
import java.util.Map;

@AllArgsConstructor
public class MethodDeserializer implements IDeserializer {

    private final Method method;

    @Override
    @SneakyThrows
    public Object deserialize(Class<?> cls, Map<String, Object> map) {
        return method.invoke(cls, map);
    }
}
