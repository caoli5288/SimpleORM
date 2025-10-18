package com.mengcraft.simpleorm;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@RequiredArgsConstructor
public abstract class Enqueuer {

    public abstract CompletableFuture<Void> enqueue(Runnable runnable);

    public abstract CompletableFuture<Void> enqueue(String ns, Runnable runnable);

    public abstract <T> CompletableFuture<T> enqueue(Supplier<T> supplier);

    public abstract <T> CompletableFuture<T> enqueue(String ns, Supplier<T> supplier);
}
