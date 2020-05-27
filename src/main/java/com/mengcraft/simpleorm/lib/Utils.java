package com.mengcraft.simpleorm.lib;

import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Utils {

    public static boolean isNullOrEmpty(String msg) {
        return msg == null || msg.isEmpty();
    }

    @SneakyThrows
    public static Method getAccessibleMethod(Class<?> cls, String methodName, Class<?>... classes) {
        Method method = cls.getDeclaredMethod(methodName, classes);
        method.setAccessible(true);
        return method;
    }

    @SneakyThrows
    public static Field getAccessibleField(Class<?> cls, String fieldName) {
        Field field = cls.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
