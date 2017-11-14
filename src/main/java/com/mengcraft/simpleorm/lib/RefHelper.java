package com.mengcraft.simpleorm.lib;

import lombok.SneakyThrows;
import lombok.val;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by on 2017/7/3.
 */
public enum RefHelper {

    INST;

    final Map<Type, Map> f = map();
    final Map<Type, Map> method = map();


    @SneakyThrows
    static Field getRef(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            if (!(type == Object.class)) {
                return getRef(type.getSuperclass(), name);
            }
            throw e;
        }
    }

    @SneakyThrows
    static Method getRef(Class<?> type, String name, Class<?>[] p) {
        try {
            val method = type.getDeclaredMethod(name, p);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            if (!(type == Object.class)) {
                return getMethodRef(type.getSuperclass(), name, p);
            }
            throw e;
        }
    }

    @SneakyThrows
    static Field getFieldRef(Class<?> type, String name) {
        Map<String, Field> map = INST.f.computeIfAbsent(type, t -> map());
        return map.computeIfAbsent(name, n -> getRef(type, name));
    }

    @SneakyThrows
    static Method getMethodRef(Class<?> type, String name, Class<?>[] p) {
        Map<String, Method> map = INST.method.computeIfAbsent(type, t -> map());
        return map.computeIfAbsent(name + "|" + Arrays.toString(p), n -> getRef(type, name, p));
    }

    static <K, V> Map<K, V> map() {
        return new ConcurrentHashMap<>();
    }

    @SneakyThrows
    public static <T> T invoke(Object any, String method, Object... input) {
        Class<?>[] p = new Class[input.length];
        for (int i = 0; i < input.length; i++) {
            p[i] = input[i].getClass();
        }
        val i = getMethodRef(any.getClass(), method, p);
        return (T) i.invoke(any, input);
    }

    @SneakyThrows
    public static <T> T getField(Object any, String field) {
        val i = getFieldRef(any.getClass(), field);
        return (T) i.get(any);
    }

}
