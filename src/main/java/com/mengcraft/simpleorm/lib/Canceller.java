package com.mengcraft.simpleorm.lib;

import lombok.NonNull;

import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class Canceller implements BiConsumer<Object, Throwable> {

    private final Future<?> f;

    public Canceller(@NonNull Future<?> f) {
        this.f = f;
    }

    public void accept(Object __, Throwable e) {
        if (e == null && !f.isDone())
            f.cancel(false);
    }
}
