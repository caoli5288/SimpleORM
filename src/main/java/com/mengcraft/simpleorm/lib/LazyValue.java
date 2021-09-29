package com.mengcraft.simpleorm.lib;

import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class LazyValue<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private T obj;

    @Override
    public T get() {
        if (obj == null) {
            obj = Objects.requireNonNull(supplier.get());
        }
        return obj;
    }

    public static <T> LazyValue<T> of(Supplier<T> supplier) {
        return new LazyValue<>(supplier);
    }
}
