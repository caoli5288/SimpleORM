package com.mengcraft.simpleorm.redis;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.RedisWrapper;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.params.SetParams;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class SimpleCache implements Closeable {

    public static final String PREFIX = "simple:l2:";
    private final RedisWrapper redis;
    private final Options options;
    private final String channel;
    private final Map<String, CompletableFuture<String>> map;

    public SimpleCache(RedisWrapper redis, Options options) {
        this.redis = redis;
        this.options = options;
        map = CacheBuilder.newBuilder()
                .expireAfterWrite(options.getExpire(), TimeUnit.SECONDS)
                .<String, CompletableFuture<String>>build()
                .asMap();
        channel = PREFIX + options.getName();
        if (options.isClustered()) {
            redis.subscribe(channel, this::msg);
        }
    }

    private void msg(byte[] bytes) {
        String msg = new String(bytes, StandardCharsets.UTF_8);
        String[] split = msg.split(",", 2);
        if (options.getId().equals(split[0])) {
            return;
        }
        expire(split[1]);
    }

    public boolean isCached(@NotNull String key) {
        return map.containsKey(key);
    }

    @NotNull
    public CompletableFuture<String> get(@NotNull String key) {
        return map.computeIfAbsent(key, this::get0);
    }

    private CompletableFuture<String> get0(String key) {
        return ORM.enqueue(() -> {
            String rKey = asRedisKey(key);
            String value = redis.getString(rKey);
            if (value == null) {
                value = options.getCalculator().apply(key);
                Preconditions.checkNotNull(value, "value is null");
                value = set0(rKey, value, key);
            }
            return value;
        });
    }

    private String set0(String rKey, String value, String key) {
        String call = redis.call(jedis -> jedis.set(rKey, value, SetParams.setParams().nx().ex(options.getExpire2())));
        if ("OK".equals(call)) {
            notify(key);
            return value;
        }
        return redis.getString(rKey);
    }

    private void notify(String key) {
        if (options.isClustered()) {
            redis.publish(channel, options.getId() + "," + key);
        }
    }

    @NotNull
    private String asRedisKey(String key) {
        return channel + ":" + key;
    }

    public void expire(@NotNull String key) {
        map.remove(key);
    }

    @NotNull
    public CompletableFuture<Boolean> expire2(@NotNull String key) {
        return ORM.enqueue(() -> {
            long call = redis.call(jedis -> jedis.del(asRedisKey(key)));
            if (call != 0) {
                notify(key);
            }
            map.remove(key);
            return call > 0;
        });
    }

    public CompletableFuture<String> set(@NotNull String key, @NotNull String value) {
        return map.compute(key, (s, old) -> ORM.enqueue(() -> set2(key, value)));
    }

    private String set2(String key, String value) {
        redis.open(jedis -> jedis.set(asRedisKey(key), value, SetParams.setParams().ex(options.getExpire2())));
        notify(key);
        return value;
    }

    @Override
    public void close() throws IOException {
        map.clear();
        if (options.isClustered()) {
            redis.unsubscribe(PREFIX + options.getName());
        }
    }

    @Builder
    @Data
    public static class Options {

        private final String id = String.valueOf(Math.random());
        private final String name;
        private final long expire;
        private final long expire2;
        private final Function<String, String> calculator;
        private final boolean clustered;
    }
}
