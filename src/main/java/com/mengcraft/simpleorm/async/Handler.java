package com.mengcraft.simpleorm.async;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static java.lang.Thread.currentThread;

@Getter
public class Handler implements Closeable {

    @Getter(AccessLevel.NONE)
    private final Map<Class<?>, BiFunction<String, Object, Object>> handlers = Maps.newHashMap();
    // const
    @Getter(AccessLevel.NONE)
    final Executor executor;
    private final ClusterSystem system;
    private final String category;
    private final String address;
    private boolean open = true;
    @Setter(AccessLevel.PACKAGE)
    private Thread context;

    Handler(ClusterSystem system, String category, String name) {
        this.system = system;
        this.category = category;
        address = system.getName() + ':' + name;
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

    public CompletableFuture<Object> sendMessage(String receiver, Object msg) {
        CompletableFuture<Object> f = new CompletableFuture<>();
        doContext(() -> system.send(this, receiver, msg, f));
        return f;
    }

    public CompletableFuture<Object> receive(Object msg) {
        CompletableFuture<Object> f = new CompletableFuture<>();
        doContext(() -> {
            try {
                Object ret = receive(address, msg);
                f.complete(ret);
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        });
        return f;
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
    public void close() {
        if (open) {
            open = false;
            system.close(this);
        }
    }

    private void doContext(Runnable commands) {
        if (currentThread() == context) {
            commands.run();
        } else {
            Utils.enqueue(executor, commands);
        }
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
