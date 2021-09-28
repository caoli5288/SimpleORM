package com.mengcraft.simpleorm.serializable;

import com.google.common.collect.Maps;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class SerializableTypes {

    private static final Map<Class<?>, IDeserializer> TYPES = Maps.newHashMap();

    @SuppressWarnings("all")
    public static IDeserializer asDeserializer(Class<?> cls) {
        return (IDeserializer) TYPES.computeIfAbsent(cls, SerializableTypes::of);
    }

    private static IDeserializer of(Class<?> cls) {
        if (cls.isAssignableFrom(ConfigurationSerializable.class)) {
            for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (isSerializableTypes(types)) {
                    constructor.setAccessible(true);
                    return new ConstructorDeserializer(constructor);
                }
            }
            for (Method method : cls.getDeclaredMethods()) {
                if ((method.getModifiers() & Modifier.STATIC) != 0 && isSerializableTypes(method.getParameterTypes())) {
                    method.setAccessible(true);
                    return new MethodDeserializer(method);
                }
            }
        }
        return GsonDeserializer.INSTANCE;
    }

    private static boolean isSerializableTypes(Class<?>[] types) {
        return types.length == 1 && types[0] == Map.class;
    }
}
