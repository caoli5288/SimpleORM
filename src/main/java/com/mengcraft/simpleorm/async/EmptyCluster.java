package com.mengcraft.simpleorm.async;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mengcraft.simpleorm.lib.Utils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class EmptyCluster implements ICluster {

    private final Multimap<String, String> refs = ArrayListMultimap.create();// cat:address
    private long number;

    @Override
    public void setup(ClusterSystem system) {

    }

    @Override
    public void reset(ClusterSystem system) {

    }

    @Override
    public void close(ClusterSystem system) {

    }

    @Override
    public CompletableFuture<?> close(ClusterSystem system, Handler ref) {
        return Utils.enqueue(system.executor, () -> refs.remove(ref.getCategory(), ref.getCategory()));
    }

    @Override
    public CompletableFuture<Message> send(ClusterSystem system, Handler caller, String address, Object obj, long fid) {
        return null;
    }

    @Override
    public CompletableFuture<Selector> query(ClusterSystem system, Selector selector) {
        switch (selector.getOps()) {
            case ONE:
                selector.getResults().add(query(selector.getCategory()));
                break;
            case MANY:
                query(selector.getCategory(), selector.getCount(), s -> selector.getResults().add(s));
                break;
            case ALL:
                selector.getResults().addAll(refs.get(selector.getCategory()));
                break;
        }
        return CompletableFuture.completedFuture(selector);
    }

    private String query(String category) {
        List<String> list = (List<String>) refs.get(category);
        int size = list.size();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return list.get(random.nextInt(size));
    }

    private void query(String category, int count, Consumer<String> consumer) {
        List<String> list = (List<String>) refs.get(category);
        int size = list.size();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            consumer.accept(list.get(random.nextInt(size)));
        }
    }

    @Override
    public CompletableFuture<String> randomName(ClusterSystem system) {
        return Utils.enqueue(system.executor, () -> Long.toHexString(number++));
    }

    @Override
    public CompletableFuture<Handler> spawn(ClusterSystem system, Handler actor) {
        return Utils.enqueue(system.executor, () -> {
            refs.put(actor.getCategory(), actor.getAddress());
            return actor;
        });
    }
}
