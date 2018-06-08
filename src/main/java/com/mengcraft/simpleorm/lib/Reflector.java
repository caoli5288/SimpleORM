package com.mengcraft.simpleorm.lib;

import lombok.SneakyThrows;
import lombok.val;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by on 2017/7/3.
 */
public enum Reflector {

    REFLECTOR;

    final Map<Type, Map<String, Constructor>> construct = map();
    final Map<Type, Map<String, Method>> method = map();
    final Map<Type, Map<String, Field>> f = map();


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
        Map<String, Field> map = REFLECTOR.f.computeIfAbsent(type, t -> map());
        return map.computeIfAbsent(name, n -> getRef(type, name));
    }

    @SneakyThrows
    static Method getMethodRef(Class<?> type, String name, Class<?>[] p) {
        Map<String, Method> map = REFLECTOR.method.computeIfAbsent(type, t -> map());
        return map.computeIfAbsent(name + "|" + Arrays.toString(p), n -> getRef(type, name, p));
    }

    static <K, V> Map<K, V> map() {
        return new ConcurrentHashMap<>();
    }

    static Class<?>[] classArray(Object[] input) {
        int len = input.length;
        Class<?>[] out = new Class[len];
        while (!(--len == -1)) {
            out[len] = input[len].getClass();
        }
        return out;
    }

    @SneakyThrows
    public static <T> T invoke(Object any, String method, Object... input) {
        Class<?>[] p = classArray(input);
        val i = getMethodRef(any.getClass(), method, p);
        return (T) i.invoke(any, input);
    }

    @SneakyThrows
    public static <T> T getField(Object any, String field) {
        val i = getFieldRef(any.getClass(), field);
        return (T) i.get(any);
    }

    @SneakyThrows
    public static void setField(Object any, String field, Object what) {
        val i = getFieldRef(any.getClass(), field);
        i.set(any, what);
    }

    @SneakyThrows
    public static <T> T object(Class<?> type, Object... param) {
        val map = REFLECTOR.construct.computeIfAbsent(type, $ -> map());
        val classArray = classArray(param);
        val ref = map.computeIfAbsent(Arrays.toString(classArray), $ -> getRef(type, classArray));
        return (T) ref.newInstance(param);
    }

    @SneakyThrows
    static Constructor getRef(Class<?> type, Class<?>[] ar) {
        val out = type.getDeclaredConstructor(ar);
        out.setAccessible(true);
        return out;
    }

}
