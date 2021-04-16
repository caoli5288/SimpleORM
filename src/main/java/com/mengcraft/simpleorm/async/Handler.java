package com.mengcraft.simpleorm.async;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

@Getter
public class Handler implements Closeable {

    @Getter(AccessLevel.NONE)
    private final Map<Class<?>, BiFunction<String, Object, Object>> handlers = Maps.newHashMap();
    @Getter(AccessLevel.NONE)
    private final Map<String, Handler> children = Maps.newConcurrentMap();
    // const
    @Getter(AccessLevel.NONE)
    final Executor executor;
    private final ClusterSystem system;
    private final Handler supervisor;
    private final String category;
    private final String address;
    private volatile boolean open = true;
    @Setter(AccessLevel.PACKAGE)
    private Thread context;
    private Consumer<Handler> constructor;
    private BiConsumer<Handler, Throwable> exceptionally;

    Handler(ClusterSystem system, Handler supervisor, String category, String address) {
        this.system = system;
        this.supervisor = supervisor;
        this.category = category;
        this.address = address;
        executor = ORM.getWorkers().of();
    }

    public <T> Handler map(Class<T> cls, BiConsumer<String, T> callback) {
        return map(cls, (s, obj) -> {
            callback.accept(s, obj);
            return null;
        });
    }

    @SuppressWarnings("all")
    public <T> Handler map(Class<T> cls, BiFunction<String, T, Object> callback) {
        handlers.put(cls, (BiFunction<String, Object, Object>) callback);
        return this;
    }

    /**
     * A.K.A. akka ask msg
     */
    public CompletableFuture<Object> sendMessage(String receiver, Object msg) {
        return system.send(this, receiver, msg);
    }

    public void fails(Throwable e) {
        fails(this, e);
    }

    void fails(Handler ref, @NonNull Throwable e) {
        if (exceptionally != null) {
            doContext(() -> exceptionally.accept(ref, e));
            return;
        }
        if (supervisor != null) {
            supervisor.fails(ref, e);
            return;
        }
        system.fails(ref, e);
    }

    public void exceptionally(BiConsumer<Handler, Throwable> exceptionally) {
        this.exceptionally = exceptionally;
    }

    public CompletableFuture<Object> receive(Object msg) {
        return Utils.enqueue(executor, () -> receive(address, msg));
    }

    Object receive(Message msg) {
        return receive(msg.getSender(), asObject(msg));
    }

    Object receive(String sender, Object obj) {
        Class<?> objCls = obj.getClass();
        return Objects.requireNonNull(handlers.get(objCls), "Unknown msg type " + objCls)
                .apply(sender, obj);
    }

    @Override
    public synchronized void close() {// synced self
        if (open) {
            open = false;
            if (supervisor != null) {
                supervisor.close(this);
            }
            for (Handler child : children.values()) {
                child.close();
            }
            children.clear();
            system.close(this);
        }
    }

    private void close(Handler child) {
        if (open) {
            children.remove(child.getAddress());// subs
        }
    }

    private void doContext(Runnable commands) {
        if (currentThread() == context) {
            commands.run();
        } else {
            Utils.enqueue(executor, commands);
        }
    }

    void setConstructor(Consumer<Handler> constructor) {
        this.constructor = constructor;
    }

    void construct() {
        constructor.accept(this);
    }

    void reset() {
        // TODO reset by supervisors
    }

    public CompletableFuture<Handler> spawn(String category, Consumer<Handler> constructor) {
        return spawn(category, constructor, false);
    }

    public CompletableFuture<Handler> spawn(String category, Consumer<Handler> constructor, boolean expose) {
        return system.spawn(this, category, constructor, expose);
    }

    /**
     * Called in child context
     */
    void addChild(Handler ref) {
        children.put(ref.getAddress(), ref);
    }

    static void complete(CompletableFuture<Object> f, Message msg) {
        if (Utils.isNullOrEmpty(msg.getContentType())) {
            f.complete(null);
        } else {
            f.complete(asObject(msg));
        }
    }

    @SneakyThrows
    static Object asObject(Message msg) {
        Gson json = ORM.json();
        return json.fromJson(json.toJsonTree(msg.getContents()), Class.forName(msg.getContentType()));
    }
}
