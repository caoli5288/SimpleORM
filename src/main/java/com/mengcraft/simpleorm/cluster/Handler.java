package com.mengcraft.simpleorm.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import io.netty.buffer.ByteBuf;
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

public abstract class Handler {

    final Map<UUID, ScheduledFuture<?>> tasks = Maps.newConcurrentMap();// async call safe
    final Set<String> listeners = Sets.newHashSet();
    // 0: init
    // 1: running
    // 2: closed
    private final AtomicInteger status = new AtomicInteger();
    Executor executor;
    private ClusterSystem system;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private long id;

    final Handler init(ClusterSystem system, Executor executor) {
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

    protected void execute(Runnable command) {
        executor.execute(command);
    }

    protected ScheduledFuture<?> schedule(Runnable command, TimeUnit unit, long delay) {
        return handle(system.executor().schedule(() -> execute(command), delay, unit));
    }

    protected ScheduledFuture<?> schedule(Runnable command, TimeUnit unit, long delay, long repeat) {
        return handle(system.executor().scheduleWithFixedDelay(() -> execute(command), delay, repeat, unit));
    }

    private ListenableScheduledFuture<?> handle(ListenableScheduledFuture<?> task) {
        UUID taskId = UUID.randomUUID();
        tasks.put(taskId, task);
        task.addListener(() -> {
            if (status() == 1) {
                tasks.remove(taskId);
            }
        }, executor);
        return task;
    }

    public CompletableFuture<Handler> sendMessage(HandlerId receiver, ByteBuf msg) {
        return system.sendMessage(this, receiver, msg);
    }

    public CompletableFuture<Long> publish(String subject, ByteBuf msg) {
        return system.publish(this, subject, msg);
    }

    public int status() {
        return status.get();
    }

    public ClusterSystem system() {
        return system;
    }
}
