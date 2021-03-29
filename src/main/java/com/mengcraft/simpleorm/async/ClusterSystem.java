package com.mengcraft.simpleorm.async;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.Getter;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

@Beta
public class ClusterSystem implements Closeable {

    private final Map<String, Handler> refs = Maps.newHashMap();
    @Getter
    private final String name;
    private final ICluster cluster;
    final Map<Long, CompletableFuture<Object>> futures = Maps.newConcurrentMap();
    final ScheduledExecutorService executor;
    Consumer<ClusterSystem> constructor;
    private Thread context;
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
    synchronized void reset() {
        if (constructor == null) {
            close();
            return;
        }
        if (open) {
            open = false;
            doContext(() -> {
                for (Handler s : refs.values()) {
                    s.close();
                }
                refs.clear();
                cluster.reset(this);
                open = true;
                constructor.accept(this);
            });
        }
    }

    void close(Handler worker) {
        doContext(() -> {
            if (open) {
                refs.remove(worker.getAddress());
            }
            cluster.close(this, worker);
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
                    e.printStackTrace();
                }
            });
        } else {
            CompletableFuture<Object> f = futures.remove(fid);
            Utils.enqueue(receiver.executor, () -> Handler.complete(f, msg))
                    .whenComplete((__, e) -> {
                        if (e != null) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    void send(Handler caller, String receiver, Object obj, CompletableFuture<Object> f) {
        if (refs.containsKey(receiver)) {
            Handler actor = refs.get(receiver);
            Utils.enqueue(actor.executor, () -> actor.receive(caller.getAddress(), obj))
                    .whenComplete((_obj, e) -> {
                        if (e == null) {
                            f.complete(_obj);
                        } else {
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
        return Utils.enqueue(executor, () -> new Handler(this, category, cluster.randomName(this)))
                .thenComposeAsync(actor -> Utils.enqueue(actor.executor, () -> {
                    actor.setContext(currentThread());
                    constructor.accept(actor);
                    return actor;
                }))
                .thenComposeAsync(actor -> Utils.enqueue(executor, () -> {
                    refs.put(actor.getAddress(), actor);
                    cluster.spawn(this, actor);
                    return actor;
                }));
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
