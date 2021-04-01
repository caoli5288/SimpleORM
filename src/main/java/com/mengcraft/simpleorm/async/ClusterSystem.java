package com.mengcraft.simpleorm.async;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.Getter;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

@Beta
public class ClusterSystem implements Closeable {

    private final Map<String, Handler> refs = Maps.newHashMap();
    @Getter
    private final String name;
    final ICluster cluster;
    final Map<Long, CompletableFuture<Object>> futures = Maps.newConcurrentMap();
    final ScheduledExecutorService executor;
    Consumer<ClusterSystem> constructor;
    private Thread context;
    private BiConsumer<Handler, Throwable> exceptionally;
    @Getter
    private volatile boolean open;

    ClusterSystem(String clusterName, String name) {
        this.name = name;
        cluster = Utils.isNullOrEmpty(clusterName)
                ? new EmptyCluster()
                : new RedisCluster(clusterName);
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setNameFormat("ClusterSystem/" + clusterName + "/" + name)
                .build());
    }

    /**
     * Call by none-supervisor handler
     */
    void fails(Handler ref, Throwable e) {
        if (exceptionally != null) {
            exceptionally.accept(ref, e);
        }
    }

    public void exceptionally(BiConsumer<Handler, Throwable> exceptionally) {
        this.exceptionally = exceptionally;
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
        doContext(() -> {
            if (open) {
                refs.remove(actor.getAddress());
            }
            if (actor.getSupervisor() == null) {
                cluster.close(this, actor);
            }
        });
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
        if (fid == -1 || !futures.containsKey(fid)) {
            Utils.enqueue(receiver.executor, () -> {
                Object let = receiver.receive(msg);
                // send ack
                cluster.send(this, receiver, msg.getSender(), let, msg.getId());
            }).whenComplete((__, e) -> {
                if (e != null) {
                    receiver.fails(e);
                }
            });
        } else {
            CompletableFuture<Object> f = futures.remove(fid);
            Utils.enqueue(receiver.executor, () -> Handler.complete(f, msg))
                    .whenComplete((__, e) -> {
                        if (e != null) {
                            receiver.fails(e);
                        }
                    });
        }
    }

    void send(Handler caller, String receiver, Object obj, CompletableFuture<Object> f) {
        if (refs.containsKey(receiver)) {
            Handler actor = refs.get(receiver);
            Utils.enqueue(actor.executor, () -> actor.receive(caller.getAddress(), obj))
                    .whenComplete((results, e) -> {
                        if (e == null) {
                            f.complete(results);
                        } else {
                            actor.fails(e);// trigger receiver first
                            f.completeExceptionally(e);
                        }
                    });
        } else {
            Message msg = cluster.send(this, caller, receiver, obj, -1);
            futures.put(msg.getId(), f);
            // ugly codes here. no native Future.orTimeout in jdk8
            Future<?> sf = executor.schedule(expire(msg.getId()), 4, TimeUnit.SECONDS);
            f.thenRun(() -> sf.cancel(false));
        }
    }

    private Runnable expire(long fid) {
        return () -> {
            CompletableFuture<?> f = futures.remove(fid);
            if (f != null) {
                f.completeExceptionally(new TimeoutException());
            }
        };
    }

    public CompletableFuture<Handler> spawn(String category, Consumer<Handler> constructor) {
        return Utils.enqueue(executor, () -> ref(null, category))
                .thenCompose(actor -> Utils.enqueue(actor.executor, () -> {
                    actor.setContext(currentThread());
                    actor.setConstructor(constructor);
                    actor.construct();
                    return actor;
                }))
                .thenCompose(actor -> Utils.enqueue(executor, () -> {
                    cluster.spawn(this, actor);
                    return actor;
                }));
    }

    CompletableFuture<Handler> spawn(Handler supervisor, String category, Consumer<Handler> constructor) {
        return Utils.enqueue(executor, () -> ref(supervisor, category))
                .thenCompose(actor -> Utils.enqueue(actor.executor, () -> {
                    actor.setContext(currentThread());
                    actor.setConstructor(constructor);
                    actor.construct();
                    return actor;
                }));
    }

    private Handler ref(Handler supervisor, String category) {
        // ask next random name from cluster instance
        String address = name + ':' + cluster.randomName(this);
        Handler ref = new Handler(this, supervisor, category, address);
        refs.put(ref.getAddress(), ref);
        return ref;
    }

    private void doContext(Runnable commands) {
        if (currentThread() != context) {
            Utils.enqueue(executor, commands);
        } else {
            commands.run();
        }
    }

    public List<String> query(Selector selector) {
        return cluster.query(this, selector);
    }

    public static CompletableFuture<ClusterSystem> create(String cluster, String name) {
        Preconditions.checkArgument(!Utils.isNullOrEmpty(name), "name cannot be empty");
        ClusterSystem system = new ClusterSystem(cluster, name);
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
        return create(cluster, Long.toHexString(ThreadLocalRandom.current().nextLong()));
    }
}