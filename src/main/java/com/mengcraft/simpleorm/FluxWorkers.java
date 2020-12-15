package com.mengcraft.simpleorm;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.Bukkit;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class FluxWorkers implements Executor, Closeable {

    private final ServerWorker serverWorker = new ServerWorker();
    private final int size;
    private final List<ExecutorService> workers;
    private int cursor;

    public FluxWorkers(int size) {
        this.size = size;
        workers = Lists.newArrayListWithCapacity(size);
        ThreadFactory factory = new ThreadFactoryBuilder()
                .setNameFormat("SimpleORM/FancyWorkers/%s")
                .build();
        for (int i = 0; i < size; i++) {
            workers.add(Executors.newSingleThreadExecutor(factory));
        }
    }

    public Executor ofServer() {
        return serverWorker;
    }

    public Executor of() {
        return this;
    }

    public Executor of(String ns) {
        cursor++;
        return workers.get(ns.hashCode() % size);
    }

    @Override
    public void execute(Runnable command) {
        workers.get(cursor++ % size).execute(command);
    }

    @Override
    public void close() {
        for (ExecutorService service : workers) {
            service.shutdown();
        }
    }

    public void awaitClose(long mills) throws InterruptedException {
        for (ExecutorService service : workers) {
            Preconditions.checkState(service.awaitTermination(mills, TimeUnit.MILLISECONDS));
        }
    }

    public static class ServerWorker implements Executor {

        @Override
        public void execute(Runnable command) {
            if (Bukkit.isPrimaryThread()) {
                command.run();
            } else {
                Bukkit.getScheduler().runTask(ORM.plugin, command);
            }
        }
    }
}
