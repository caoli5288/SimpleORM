package com.mengcraft.simpleorm.async;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

@Beta
@ExtensionMethod(Utils.class)
public class ClusterSystem implements Closeable {

    private final Map<String, Handler> refs = Maps.newConcurrentMap();
    @Getter
    private final String name;
    @Getter
    private final ClusterOptions options;
    final ICluster cluster;
    final Map<Long, CompletableFuture<Object>> callbacks = Maps.newConcurrentMap();
    final ScheduledExecutorService executor;
    Consumer<ClusterSystem> constructor;
    private Thread context;
    @Getter
    private volatile boolean open;

    ClusterSystem(String clusterName, String name, ClusterOptions options) {
        this.name = name;
        this.options = options;
        cluster = Utils.isNullOrEmpty(clusterName)
                ? new EmptyCluster()
                : new RedisCluster(clusterName);
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setNameFormat("ClusterSystem/" + clusterName + "/" + name)
                .build());
    }

    public ScheduledExecutorService scheduler() {
        return executor;
    }

    /**
     * Call by none-supervisor handler
     */
    void fails(Handler ref, Throwable e) {
        if (e instanceof RuntimeException) {// reset with un-catches runtime exceptions
            ref.reset();
        } else {// close other wises
            ref.close();
        }
    }

    public void constructor(Consumer<ClusterSystem> constructor) {
        Preconditions.checkState(this.constructor == null, "call constructor twice");
        this.constructor = constructor;
        constructor.accept(this);
    }

    void setup() {
        context = currentThread();
        cluster.setup(this);
        open = true;
    }

    @Override
    public synchronized void close() {
        if (open) {
            open = false;
            doContext(() -> {
                for (Handler s : refs.values()) {
                    s.close();
                }
                refs.clear();
                cluster.close(this);
                executor.shutdown();
            });
        }
    }

    /**
     * Call by ICluster while heartbeat fail
     */
    void reset() {
        if (constructor == null) {
            close();
            return;
        }
        if (open) {
            open = false;
            // system contexts
            for (Handler s : refs.values()) {
                s.close();
            }
            refs.clear();
            cluster.reset(this);
            open = true;
            constructor.accept(this);
        }
    }

    void close(Handler actor) {// called from actor context
        if (open) {
            refs.remove(actor.getAddress());
        }
        if (actor.exposed) {
            cluster.close(this, actor);
        }
    }

    void receive(byte[] bytes) {
        doContext(() -> {
            Message msg = ORM.json().fromJson(new String(bytes, StandardCharsets.UTF_8), Message.class);
            receive(msg);
        });
    }

    void receive(Message msg) {
        Handler receiver = refs.get(msg.getReceiver());
        long fid = msg.getFutureId();
        if (fid == -1) {
            Utils.enqueue(receiver.executor, () -> receiver.receive(msg)).unpack()
                    .thenApply(obj -> cluster.send(this, receiver, msg.getSender(), obj, msg.getId()))
                    .whenComplete((__, e) -> {
                        if (e != null) {
                            receiver.fails(e);
                        }
                    });
        } else if (callbacks.containsKey(fid)) {
            CompletableFuture<Object> f = callbacks.remove(fid);
            Utils.enqueue(receiver.executor, () -> Handler.complete(f, msg))
                    .whenComplete((__, e) -> {
                        if (e != null) {
                            receiver.fails(e);
                        }
                    });
        }
        // TODO handle un-ack msg
    }

    CompletableFuture<Object> send(Handler caller, String receiver, Object obj) {
        Handler ref = refs.get(receiver);
        if (ref != null) {// local actors
            return Utils.enqueue(ref.executor, () -> ref.receive(caller.getAddress(), obj)).unpack()
                    .thenApplyAsync(r -> r, caller.executor);// compose to caller executor ctx
        }
        return cluster.send(this, caller, receiver, obj, -1)
                .thenCompose(msg -> {
                    CompletableFuture<Object> f = Utils.orTimeout(Utils.future(), executor, 4, TimeUnit.SECONDS)
                            .whenComplete((__, e) -> {
                                if (e != null)
                                    callbacks.remove(msg.getId());
                            });
                    callbacks.put(msg.getId(), f);
                    return f;
                });
    }

    public CompletableFuture<Handler> spawn(String category, Consumer<Handler> constructor) {
        return ref(null, category, true)
                .thenCompose(actor -> Utils.enqueue(actor.executor, () -> {
                    actor.setContext(currentThread());
                    actor.setConstructor(constructor);
                    actor.construct();
                    return actor;
                }))
                .thenCompose(actor -> cluster.spawn(this, actor));
    }

    CompletableFuture<Handler> spawn(Handler supervisor, String category, Consumer<Handler> constructor, boolean expose) {
        CompletableFuture<Handler> f = ref(supervisor, category, expose)
                .thenCompose(actor -> Utils.enqueue(actor.executor, () -> {
                    actor.setContext(currentThread());
                    actor.setConstructor(constructor);
                    actor.construct();
                    actor.getSupervisor().addChild(actor);
                    return actor;
                }));
        if (expose) {
            return f.thenCompose(actor -> cluster.spawn(this, actor));
        }
        return f;
    }

    private CompletableFuture<Handler> ref(Handler supervisor, String category, boolean exposed) {
        // ask next random name from cluster instance
        return cluster.randomName(this, exposed)
                .thenApply(s -> {
                    String address = name + ':' + s;
                    Handler ref = new Handler(this, supervisor, category, address, exposed);
                    refs.put(ref.getAddress(), ref);
                    return ref;
                });
    }

    private void doContext(Runnable commands) {
        if (currentThread() != context) {
            Utils.enqueue(executor, commands);
        } else {
            commands.run();
        }
    }

    public CompletableFuture<Selector> query(Selector selector) {
        return cluster.query(this, selector);
    }

    public static CompletableFuture<ClusterSystem> create(String cluster, String name) {
        return create(cluster, name, ClusterOptions.builder().build());
    }

    public static CompletableFuture<ClusterSystem> create(String cluster, String name, ClusterOptions options) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(name), "name cannot be empty");
        ClusterSystem system = new ClusterSystem(cluster, name, options);
        return Utils.enqueue(system.executor, () -> {
            system.setup();
            return system;
        }).whenComplete((res, e) -> {
            if (e != null) {
                system.close();
            }
        });
    }

    public static CompletableFuture<ClusterSystem> create(String cluster) {
        return create(cluster, Long.toHexString(ThreadLocalRandom.current().nextLong()), ClusterOptions.builder().build());
    }
}
