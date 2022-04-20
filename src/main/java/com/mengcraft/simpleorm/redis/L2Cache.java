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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class L2Cache implements Closeable {

    public static final String PREFIX = "simple:l2:";
    private final RedisWrapper redis;
    private final Options options;
    private final Map<String, CompletableFuture<String>> map;

    public L2Cache(RedisWrapper redis, Options options) {
        this.redis = redis;
        this.options = options;
        map = CacheBuilder.newBuilder()
                .expireAfterWrite(options.getExpire(), TimeUnit.SECONDS)
                .<String, CompletableFuture<String>>build()
                .asMap();
        if (options.isClustered()) {
            redis.subscribe(PREFIX + options.getName(), bytes -> expire(new String(bytes)));
        }
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
            String key2 = asRedisKey(key);
            String value = redis.getString(key2);
            if (value == null) {
                value = options.getCalculator().apply(key);
                Preconditions.checkNotNull(value, "value is null");
                value = set0(key2, value);
            }
            return value;
        });
    }

    private String set0(String key, String value) {
        String call = redis.call(jedis -> jedis.set(key, value, SetParams.setParams().nx().ex(options.getExpire2())));
        if (!"OK".equals(call)) {
            return redis.getString(key);
        }
        return value;
    }

    @NotNull
    private String asRedisKey(String key) {
        return PREFIX + options.getName() + ":" + key;
    }

    public void expire(@NotNull String key) {
        map.remove(key);
    }

    @NotNull
    public CompletableFuture<Boolean> expire2(@NotNull String key) {
        return ORM.enqueue(() -> {
            long call = redis.call(jedis -> jedis.del(asRedisKey(key)));
            map.remove(key);
            return call > 0;
        });
    }

    public CompletableFuture<String> set(@NotNull String key, @NotNull String value) {
        return map.compute(key, (s, old) -> ORM.enqueue(() -> set2(asRedisKey(key), value)));
    }

    private String set2(String key, String value) {
        redis.open(jedis -> jedis.set(key, value, SetParams.setParams().ex(options.getExpire2())));
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

        private final String name;
        private final long expire;
        private final long expire2;
        private final Function<String, String> calculator;
        private final boolean clustered;
    }
}
