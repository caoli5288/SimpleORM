package com.mengcraft.simpleorm;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleFuture<T> {

    private CompletableFuture<T> future;
    private Executor executor;

    private SimpleFuture(CompletableFuture<T> future, Executor executor) {
        this.future = future;
        this.executor = executor;
    }

    public SimpleFuture<T> sync() {
        return async(ORM.getWorkers().ofServer());
    }

    public SimpleFuture<T> async() {
        return async(ORM.getWorkers().of());
    }

    public SimpleFuture<T> async(String eid) {
        return async(ORM.getWorkers().of(eid));
    }

    public SimpleFuture<T> async(Executor executor) {
        this.executor = executor;
        return this;
    }

    public SimpleFuture<T> complete(Runnable runnable) {
        future = (CompletableFuture<T>) future.thenRunAsync(runnable, executor);
        return this;
    }

    public SimpleFuture<T> complete(Consumer<T> consumer) {
        future = (CompletableFuture<T>) future.thenAcceptAsync(consumer, executor);
        return this;
    }

    public SimpleFuture<T> complete(BiConsumer<T, Throwable> consumer) {
        future = future.whenCompleteAsync(consumer, executor);
        return this;
    }

    public <R> SimpleFuture<R> then(Supplier<R> supplier) {
        future = (CompletableFuture<T>) future.thenApplyAsync(s -> supplier.get(), executor);
        return (SimpleFuture<R>) this;
    }

    public <R> SimpleFuture<R> then(Function<T, R> function) {
        future = (CompletableFuture<T>) future.thenApplyAsync(function, executor);
        return (SimpleFuture<R>) this;
    }

    public <U, R> SimpleFuture<R> thenCombine(SimpleFuture<U> composite, BiFunction<T, U, R> function) {
        future = (CompletableFuture<T>) future.thenCombineAsync(composite.future(), function, executor);
        return (SimpleFuture<R>) this;
    }

    public <R> SimpleFuture<R> thenCompose(Function<T, SimpleFuture<R>> function) {
        future = (CompletableFuture<T>) future.thenComposeAsync(s -> function.apply(s).future(), executor);
        return (SimpleFuture<R>) this;
    }

    public SimpleFuture<T> orElse(T value) {// orCompose
        future = future.exceptionally(t -> value);
        return this;
    }

    public CompletableFuture<T> future() {
        return future;
    }

    public static <T> SimpleFuture<T> of() {
        return of(null);
    }

    public static <T> SimpleFuture<T> of(T value) {
        return of(CompletableFuture.completedFuture(value));
    }

    public static <T> SimpleFuture<T> of(CompletableFuture<T> future) {
        return new SimpleFuture<>(future, MoreExecutors.directExecutor());
    }
}
