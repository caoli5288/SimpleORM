package com.mengcraft.simpleorm.async;

import java.util.concurrent.CompletableFuture;

public interface ICluster {

    void setup(ClusterSystem system);

    void reset(ClusterSystem system);

    void close(ClusterSystem system);

    CompletableFuture<?> close(ClusterSystem system, Handler actor);

    CompletableFuture<Message> send(ClusterSystem system, Handler caller, String address, Object obj, long fid);

    CompletableFuture<Handler> spawn(ClusterSystem system, Handler actor);

    CompletableFuture<String> randomName(ClusterSystem system, boolean expose);

    CompletableFuture<Selector> query(ClusterSystem system, Selector selector);
}
