package com.mengcraft.simpleorm.lib;

import lombok.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public final class Timeout implements Runnable {
    final CompletableFuture<?> f;

    public Timeout(@NonNull CompletableFuture<?> f) {
        this.f = f;
    }

    public void run() {
        if (!f.isDone())
            f.completeExceptionally(new TimeoutException());
    }
}
