package com.mengcraft.simpleorm.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Handler {

    final Map<UUID, ScheduledFuture<?>> tasks = Maps.newConcurrentMap();// async call safe
    final Set<String> listeners = Sets.newHashSet();
    // 0: init
    // 1: running
    // 2: closed
    private final AtomicInteger status = new AtomicInteger();
    EventLoop executor;
    private ClusterSystem system;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private long id;

    final Handler init(ClusterSystem system, EventLoop executor) {
        Preconditions.checkState(status.compareAndSet(0, 1));
        this.system = system;
        this.executor = executor;
        onInit();
        return this;
    }

    protected CompletableFuture<Handler> listen(String subject) {
        Preconditions.checkState(listeners.add(subject), "Already listened subject: " + subject);
        return system.listen(this, subject);
    }

    protected void onInit() {

    }

    protected void onClose() {

    }

    public CompletableFuture<Handler> close() {
        Preconditions.checkState(status.compareAndSet(1, 2));
        return system.close(this);
    }

    protected void onMessage(String subject, HandlerId sender, ByteBuf msg) {

    }

    protected <T> T jedis(Function<Jedis, T> function) {
        return system.jedis.call(function);
    }

    protected CompletableFuture<Void> execute(Runnable command) {
        return CompletableFuture.runAsync(command, executor);
    }

    protected <T> CompletableFuture<T> execute(Supplier<T> command) {
        return CompletableFuture.supplyAsync(command, executor);
    }

    protected ScheduledFuture<?> schedule(Runnable command, TimeUnit unit, long delay) {
        Preconditions.checkState(status.get() == 1, "Handler is closed");
        return handle(executor.schedule(command, delay, unit));
    }

    protected ScheduledFuture<?> schedule(Runnable command, TimeUnit unit, long initDelay, long delay) {
        Preconditions.checkState(status.get() == 1, "Handler is closed");
        return handle(executor.scheduleWithFixedDelay(command, initDelay, delay, unit));
    }

    private ScheduledFuture<?> handle(ScheduledFuture<?> task) {
        UUID taskId = UUID.randomUUID();
        tasks.put(taskId, task);
        // Cast it
        ((Promise<?>) task).addListener(f -> tasks.remove(taskId));
        return task;
    }

    public CompletableFuture<Handler> sendMessage(HandlerId receiver, ByteBuf msg) {
        return system.sendMessage(this, receiver, msg);
    }

    public CompletableFuture<Long> publish(String subject, ByteBuf msg) {
        return system.publish(this, subject, msg);
    }

    protected Executor executor() {
        return executor;
    }

    protected Void exceptionally(Throwable throwable) {
        // no-ops by default
        return null;
    }

    public int status() {
        return status.get();
    }

    public ClusterSystem system() {
        return system;
    }
}
