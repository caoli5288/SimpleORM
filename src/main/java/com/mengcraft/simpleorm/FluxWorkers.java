package com.mengcraft.simpleorm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mengcraft.simpleorm.lib.Types;
import com.mengcraft.simpleorm.lib.Utils;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import lombok.SneakyThrows;

import java.io.Closeable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

public class FluxWorkers implements Executor, Closeable {

    private final int size;
    private final EventLoop[] executors;
    private final DefaultEventLoopGroup elg;
    private final Executor[] vtPool;
    private int vts;// It's safe to use un-threadsafe indexer
    private static MethodHandle vtHandle;

    static {
        loadVtHandle();
    }

    public static boolean isVtEnabled() {
        return vtHandle != null;
    }

    public FluxWorkers(int size) {
        this.size = size;
        executors = new EventLoop[size];
        ThreadFactory factory = new ThreadFactoryBuilder()
                .setNameFormat("ORM-Workers-%s")
                .build();
        elg = new DefaultEventLoopGroup(size, factory);
        for (int i = 0; i < size; i++) {
            executors[i] = elg.next();
        }
        vtPool = new Executor[size];
        if (isVtEnabled()) {
            for (int i = 0; i < size; i++) {
                vtPool[i] = ofVirtual(executors[i]);
            }
        } else {
            System.arraycopy(executors, 0, vtPool, 0, size);
        }
    }

    static void loadVtHandle() {
        try {
            Class<?> cls = Class.forName("java.lang.ThreadBuilders");
            for (Method method : cls.getDeclaredMethods()) {
                if (method.getName().equals("newVirtualThread")) {
                    MethodHandles.Lookup lookup = Types.lookupPrivileged(cls);
                    vtHandle = lookup.unreflect(method);
                }
            }
            // log
            ORM.plugin.getLogger().info("VirtualThreads enabled=" +
                    (vtHandle != null) +
                    ".");
        } catch (Exception ex) {
            ORM.plugin.getLogger().warning("VirtualThreads not supported. " + ex.getMessage());
        }
    }

    public Executor ofServer() {
        return ORM.plugin;
    }

    public Executor of() {
        return elg.next();
    }

    public Executor of(String ns) {
        return of(ns.hashCode());
    }

    public Executor of(int i) {
        return executors[(i & Integer.MAX_VALUE) % size];
    }

    public Executor ofVirtual() {
        return ofVirtual(vts++);
    }

    public Executor ofVirtual(String plugin) {
        return ofVirtual(plugin.hashCode());
    }

    public Executor ofVirtual(int slot) {
        return vtPool[(slot & Integer.MAX_VALUE) % size];
    }

    public Executor ofVirtual(Executor handle) {
        if (vtHandle == null) {
            // log
            ORM.plugin.getLogger().log(Level.WARNING, "VirtualThreads not supported.", new UnsupportedOperationException("ofVirtual(Executor)"));
            return handle;
        }
        return t -> startVirtual(handle, t);
    }

    @SneakyThrows
    static void startVirtual(Executor scheduler, Runnable task) {
        ((Thread) vtHandle.invokeExact(scheduler, (String) null, 0, task)).start();
    }

    @Override
    public void execute(Runnable command) {
        of().execute(command);
    }

    @Override
    public void close() {
        elg.shutdownGracefully();
    }

    public void awaitClose(long mills) throws InterruptedException {
        elg.awaitTermination(mills, TimeUnit.MILLISECONDS);
    }

    // Enqueuer
    public Enqueuer ofEnqueuer() {
        return new Enqueuer() {
            @Override
            public CompletableFuture<Void> enqueue(Runnable runnable) {
                return Utils.enqueue(of(), runnable);
            }

            @Override
            public CompletableFuture<Void> enqueue(String ns, Runnable runnable) {
                return Utils.enqueue(of(ns), runnable);
            }

            @Override
            public <T> CompletableFuture<T> enqueue(Supplier<T> supplier) {
                return Utils.enqueue(of(), supplier);
            }

            @Override
            public <T> CompletableFuture<T> enqueue(String ns, Supplier<T> supplier) {
                return Utils.enqueue(of(ns), supplier);
            }
        };
    }

    public Enqueuer ofVirtualEnqueuer() {
        return new Enqueuer() {
            @Override
            public CompletableFuture<Void> enqueue(Runnable runnable) {
                return Utils.enqueue(ofVirtual(), runnable);
            }

            @Override
            public CompletableFuture<Void> enqueue(String ns, Runnable runnable) {
                return Utils.enqueue(ofVirtual(ns), runnable);
            }

            @Override
            public <T> CompletableFuture<T> enqueue(Supplier<T> supplier) {
                return Utils.enqueue(ofVirtual(), supplier);
            }

            @Override
            public <T> CompletableFuture<T> enqueue(String ns, Supplier<T> supplier) {
                return Utils.enqueue(ofVirtual(ns), supplier);
            }
        };
    }
}
