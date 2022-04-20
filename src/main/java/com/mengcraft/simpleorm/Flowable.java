package com.mengcraft.simpleorm;

import com.google.common.util.concurrent.MoreExecutors;
import com.mengcraft.simpleorm.lib.Utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Flowable<T> {

    private CompletableFuture<T> future;
    private Executor executor;

    private Flowable(CompletableFuture<T> future, Executor executor) {
        this.future = future;
        this.executor = executor;
    }

    public Flowable<T> sync() {
        return async(ORM.getWorkers().ofServer());
    }

    public Flowable<T> async() {
        return async(ORM.getWorkers().of());
    }

    public Flowable<T> async(String eid) {
        return async(ORM.getWorkers().of(eid));
    }

    public Flowable<T> async(Executor executor) {
        this.executor = executor;
        return this;
    }

    public Flowable<T> complete(Runnable runnable) {
        future = (CompletableFuture<T>) future.thenRunAsync(runnable, executor);
        return this;
    }

    public Flowable<T> complete(Consumer<T> consumer) {
        future = (CompletableFuture<T>) future.thenAcceptAsync(consumer, executor);
        return this;
    }

    public <R> Flowable<R> then(Supplier<R> supplier) {
        future = (CompletableFuture<T>) future.thenApplyAsync(s -> supplier.get(), executor);
        return (Flowable<R>) this;
    }

    public <R> Flowable<R> then(Function<T, R> function) {
        future = (CompletableFuture<T>) future.thenApplyAsync(function, executor);
        return (Flowable<R>) this;
    }

    public <U, R> Flowable<R> thenCombine(Flowable<U> composite, BiFunction<T, U, R> function) {
        future = (CompletableFuture<T>) future.thenCombineAsync(composite.future(), function, executor);
        return (Flowable<R>) this;
    }

    public <R> Flowable<R> thenCompose(Function<T, Flowable<R>> function) {
        future = (CompletableFuture<T>) future.thenComposeAsync(s -> function.apply(s).future(), executor);
        return (Flowable<R>) this;
    }

    public Flowable<T> orElse(T value) {// orCompose
        future = future.exceptionally(t -> value);
        return this;
    }

    public CompletableFuture<T> future() {
        return future;
    }

    public static <T> Flowable<T> of() {
        return new Flowable<>(CompletableFuture.completedFuture(null), MoreExecutors.directExecutor());
    }
}
