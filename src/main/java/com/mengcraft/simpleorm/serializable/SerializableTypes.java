package com.mengcraft.simpleorm.serializable;

import com.google.common.collect.Maps;
import lombok.SneakyThrows;
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

    @SneakyThrows
    private static IDeserializer of(Class<?> cls) {
        if (ConfigurationSerializable.class.isAssignableFrom(cls)) {
            // See https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/configuration/serialization/ConfigurationSerializable.html
            for (Method method : cls.getDeclaredMethods()) {
                if (isDeserializer(cls, method)) {
                    method.setAccessible(true);
                    return new MethodDeserializer(method);
                }
            }
            for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (isSerializableTypes(types)) {
                    constructor.setAccessible(true);
                    return new ConstructorDeserializer(constructor);
                }
            }
        }
        return GsonDeserializer.INSTANCE;
    }

    private static boolean isSerializableTypes(Class<?>[] types) {
        return types.length == 1 && types[0] == Map.class;
    }

    static boolean isDeserializer(Class<?> cls, Method method) {
        // check if static
        if ((method.getModifiers() & Modifier.STATIC) == 0) {
            return false;
        }
        String methodName = method.getName();
        if (methodName.equals("deserialize") || methodName.equals("valueOf")) {
            return cls == method.getReturnType() && isSerializableTypes(method.getParameterTypes());
        }
        return false;
    }
}
