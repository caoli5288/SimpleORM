package com.mengcraft.simpleorm.lib;

import lombok.SneakyThrows;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Created by on 1月7日.
 */
public enum MessageDigestLocal {

    INSTANCE;

    private final Map<String, ThreadLocal<MessageDigest>> all = new ConcurrentHashMap<>();

    public static MessageDigest algorithm(String algorithm) {
        ThreadLocal<MessageDigest> local = INSTANCE.all.get(algorithm);
        if (local == null) {
            local = new SuppliedLocal<>(() -> load(algorithm));
            INSTANCE.all.put(algorithm, local);
        }
        return local.get();
    }

    @SneakyThrows
    static MessageDigest load(String algorithm) {
        return MessageDigest.getInstance(algorithm);
    }

    static final class SuppliedLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        protected T initialValue() {
            return supplier.get();
        }
    }

}
