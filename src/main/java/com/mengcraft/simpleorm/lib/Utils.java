package com.mengcraft.simpleorm.lib;

import com.google.common.io.ByteStreams;
import com.mengcraft.simpleorm.async.Handler;
import lombok.SneakyThrows;

import javax.persistence.Table;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Utils {

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Object> unpack(CompletableFuture<Object> future) {
        return future.thenCompose(obj -> {
            if (obj instanceof CompletableFuture) {
                return unpack((CompletableFuture<Object>) obj);
            }
            return CompletableFuture.completedFuture(obj);
        });
    }

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

    /**
     * Copy from gson {@code FieldNamingPolicy}.
     *
     * @see com.google.gson.FieldNamingPolicy
     */
    public static String separateCamelCase(String name, String separator) {
        StringBuilder translation = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isUpperCase(character) && translation.length() != 0) {
                translation.append(separator);
            }
            translation.append(character);
        }
        return translation.toString();
    }

    public static String translateSqlName(Class<?> cls) {
        Table a = cls.getAnnotation(Table.class);
        if (a != null && !isNullOrEmpty(a.name())) {
            return a.name();
        }
        return separateCamelCase(cls.getSimpleName(), "_").toLowerCase();
    }

    private static final Method URL_CLASS_LOADER_addURL = getAccessibleMethod(URLClassLoader.class, "addURL", URL.class);

    @SneakyThrows
    public static void addUrl(URLClassLoader cl, URL url) {
        URL_CLASS_LOADER_addURL.invoke(cl, url);
    }

    public static <T> CompletableFuture<T> enqueue(Executor executor, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public static CompletableFuture<Void> enqueue(Executor executor, Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    @SneakyThrows
    public static String toString(InputStream resource) {
        return new String(ByteStreams.toByteArray(resource), StandardCharsets.UTF_8);
    }

    public static InputStream getResourceStream(String s) {
        return Utils.class.getClassLoader().getResourceAsStream(s);
    }

    public static <T> void let(T obj, Consumer<T> consumer) {
        if (obj != null) {
            consumer.accept(obj);
        }
    }

    public static <T> CompletableFuture<T> orTimeout(CompletableFuture<T> f, ScheduledExecutorService executor, long t, TimeUnit unit) {
        return f.whenComplete(new Canceller(executor.schedule(new Timeout(f), t, unit)));
    }

    public static <T> CompletableFuture<T> future() {
        return new CompletableFuture<>();
    }

    public static boolean isNullOrClosed(Handler ref) {
        return ref == null || !ref.isOpen();
    }
}
