package com.mengcraft.simpleorm.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mengcraft.simpleorm.FluxWorkers;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.RedisWrapper;
import com.mengcraft.simpleorm.lib.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ClusterSystem {

    @Getter
    private final String name;
    // TODO configurable
    private final FluxWorkers executors = ORM.getWorkers();
    private final Map<Long, Handler> handlerMap = Maps.newHashMap();
    private final Map<String, List<Handler>> listenerMap = Maps.newHashMap();
    private final Map<Class<? extends Handler>, Deploy> deployMap = Maps.newHashMap();
    private final AtomicInteger status = new AtomicInteger();
    final RedisWrapper jedis = ORM.globalRedisWrapper();
    private ListeningScheduledExecutorService executor;
    @Getter
    private long id;

    ClusterSystem(String name) {
        this.name = name;
    }

    public int status() {
        return status.get();
    }

    public CompletableFuture<ClusterSystem> start() {
        Preconditions.checkState(status.compareAndSet(0, 1));
        executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setNameFormat("simple-cluster-" + name)
                .build()));
        return Utils.enqueue(executor, () -> {
            init();
            return this;
        });
    }

    CompletableFuture<Handler> close(Handler handler) {
        if (status() == 2) {
            return Utils.enqueue(handler.executor, () -> Utils.let(handler, Handler::onClose));
        }
        return Utils.enqueue(executor, () -> doClose(handler))
                .thenApplyAsync(obj -> Utils.let(obj, Handler::onClose), handler.executor);
    }

    private Handler doClose(Handler handler) {
        handlerMap.remove(handler.getId());
        for (ScheduledFuture<?> future : handler.tasks.values()) {
            future.cancel(false);
        }
        for (String listener : handler.listeners) {
            List<Handler> list = listenerMap.get(listener);
            list.remove(handler);
            if (list.isEmpty()) {
                jedis.unsubscribe("cluster:" + name + ":0:" + listener);
                listenerMap.remove(listener);
            }
        }
        return handler;
    }

    public CompletableFuture<?> close() {
        Preconditions.checkState(status.compareAndSet(1, 2));
        // switch context first
        return Utils.enqueue(executor, () -> handlerMap.values()
                        .stream()
                        .map(Handler::close)
                        .toArray(CompletableFuture<?>[]::new))
                .thenCompose(CompletableFuture::allOf)
                .thenRun(() -> {
                    jedis.unsubscribe("cluster:" + name + ":" + id);
                    listenerMap.forEach((listener, list) -> jedis.unsubscribe("cluster:" + name + ":0:" + listener));
                    MoreExecutors.shutdownAndAwaitTermination(executor, 1, TimeUnit.MINUTES);
                });
    }

    private void init() {
        id = jedis.call(cli -> cli.incr("cluster:" + name + ":ref"));
        jedis.subscribe("cluster:" + name + ":" + id, this::onMessage);
    }

    CompletableFuture<Handler> listen(Handler handler, String subject) {
        return Utils.enqueue(executor, () -> listen0(handler, subject))
                .thenApplyAsync(__ -> handler, handler.executor);// switch to handler context
    }

    private void listen0(Handler handler, String subject) {
        if (listenerMap.containsKey(subject)) {
            listenerMap.get(subject).add(handler);
        } else {
            listenerMap.put(subject, Lists.newArrayList(handler));
            jedis.subscribe("cluster:" + name + ":0:" + subject, ofListener(subject));
        }
    }

    private void onMessage(byte[] bytes) {
        Utils.enqueue(executor, () -> {
            ByteBuf msg = Unpooled.wrappedBuffer(bytes);
            msg.readByte();// byte: msg type
            // long: receiver id
            // long: sender system id
            // long: sender id
            // bytes: remaining data
            long handlerId = msg.readLong();
            Handler handler = handlerMap.get(handlerId);
            if (handler != null) {
                HandlerId senderId = new HandlerId(msg.readLong(), msg.readLong());
                handler.execute(() -> handler.onMessage("", senderId, msg));
            }
        });
    }

    private Consumer<byte[]> ofListener(String subject) {
        return bytes -> {
            // long: sender system id 
            // long: sender id
            // bytes: remaining data
            Utils.enqueue(executor, () -> {
                List<Handler> list = listenerMap.get(subject);
                if (!Utils.isNullOrEmpty(list)) {
                    ByteBuf msg = Unpooled.wrappedBuffer(bytes);
                    HandlerId senderId = new HandlerId(msg.readLong(), msg.readLong());
                    for (Handler handler : list) {
                        ByteBuf copy = msg.copy();
                        handler.execute(() -> handler.onMessage(subject, senderId, copy));
                    }
                }
            });
        };
    }

    CompletableFuture<Long> publish(Handler sender, String subject, ByteBuf msg) {
        return Utils.enqueue(sender.executor, () -> {
            ByteBuf data = Unpooled.buffer();
            data.writeLong(id);
            data.writeLong(sender.getId());
            data.writeBytes(msg);
            ReferenceCountUtil.safeRelease(msg);
            byte[] bytes = Arrays.copyOfRange(data.array(), data.arrayOffset(), data.readableBytes());
            return jedis.call(cli -> cli.publish(("cluster:" + name + ":0:" + subject).getBytes(StandardCharsets.UTF_8), bytes));
        });
    }

    CompletableFuture<Handler> sendMessage(Handler sender, HandlerId receiver, ByteBuf msg) {
        if (receiver.getSystemId() == id) {
            return Utils.enqueue(executor, () -> loopBack(sender, receiver, msg))
                    .thenApplyAsync(s -> s, sender.executor);// switch context to handler
        } else {
            return Utils.enqueue(sender.executor, () -> {
                ByteBuf data = Unpooled.buffer();
                // 0: plain msg
                data.writeByte(0);
                // plain msg headers
                data.writeLong(receiver.getId());
                data.writeLong(id);
                data.writeLong(sender.getId());
                data.writeBytes(msg);
                ReferenceCountUtil.safeRelease(msg);
                // then send to redis channel
                byte[] bytes = Arrays.copyOfRange(data.array(), data.arrayOffset(), data.readableBytes());
                jedis.publish("cluster:" + name + ":" + receiver.getSystemId(), bytes);
                return sender;
            });
        }
    }

    private Handler loopBack(Handler sender, HandlerId receiver, ByteBuf msg) {
        Handler handler = handlerMap.get(receiver.getId());
        if (handler != null) {
            HandlerId senderId = new HandlerId(id, sender.getId());
            ByteBuf copy = Unpooled.copiedBuffer(msg);// non-pooled heap copy
            ReferenceCountUtil.safeRelease(msg);
            handler.execute(() -> handler.onMessage("", senderId, copy));
        }
        return sender;
    }

    public CompletableFuture<Handler> deploy(Handler handler) {
        Preconditions.checkState(status() == 1);
        Executor bind = executors.of();
        return Utils.enqueue(executor, () -> init(handler))
                .thenApplyAsync(result -> result.init(this, bind), bind);
    }

    public CompletableFuture<Handler> deploy(Class<? extends Handler> cls, DeployOptions options) {
        Preconditions.checkState(status() == 1);
        return Utils.enqueue(executor, () -> {
            Preconditions.checkState(!deployMap.containsKey(cls));
            Deploy deploy = new Deploy(cls, options);
            deployMap.put(cls, deploy);
            return deploy;
        }).thenCompose(this::deploy);
    }

    @NotNull
    private Handler init(Handler handler) {
        Preconditions.checkState(handler.status() == 0);
        long handlerId = jedis.call(cli -> cli.incr("cluster:" + name + ":ref"));
        handler.setId(handlerId);
        handlerMap.put(handlerId, handler);
        return handler;
    }

    ListeningScheduledExecutorService executor() {
        return executor;
    }

    public static ClusterSystem ofName(String name) {
        return new ClusterSystem(name);
    }
}
