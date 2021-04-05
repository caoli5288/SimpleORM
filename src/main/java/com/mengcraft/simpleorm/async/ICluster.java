package com.mengcraft.simpleorm.async;

import java.util.concurrent.CompletableFuture;

public interface ICluster {

    void setup(ClusterSystem system);

    void reset(ClusterSystem system);

    void close(ClusterSystem system);

    void close(ClusterSystem system, Handler actor);

    CompletableFuture<Message> send(ClusterSystem system, Handler caller, String address, Object obj, long fid);

    void spawn(ClusterSystem system, Handler actor);

    String randomName(ClusterSystem system);

    CompletableFuture<Selector> query(ClusterSystem system, Selector selector);
}
