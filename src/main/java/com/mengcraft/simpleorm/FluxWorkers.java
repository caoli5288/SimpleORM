package com.mengcraft.simpleorm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class FluxWorkers implements Executor, Closeable {

    private final int size;
    private final EventLoop[] executors;
    private final DefaultEventLoopGroup elg;

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
}
