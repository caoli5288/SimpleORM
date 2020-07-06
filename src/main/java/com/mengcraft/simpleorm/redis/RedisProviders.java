package com.mengcraft.simpleorm.redis;

import com.mengcraft.simpleorm.provider.IRedisProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RedisProviders {

    public static IRedisProvider of(String sentinel, String url, int conn) {
        GenericObjectPoolConfig<?> config = new GenericObjectPoolConfig<>();
        if (conn >= 1) {
            config.setMaxTotal(conn);
        }
        if (sentinel == null || sentinel.isEmpty()) {
            return new GenericProvider(new JedisPool(config, URI.create(url)));
        }
        Set<String> sentinels = new HashSet<>();
        if (url.matches("redis://(.+[,].+)")) {
            Collections.addAll(sentinels, url.substring(8).split(","));
        } else {
            String[] split = url.split(";");
            for (String line : split) {
                URI uri = URI.create(line);
                sentinels.add(uri.getHost() + ':' + uri.getPort());
            }
        }
        return new SentinelProvider(new JedisSentinelPool(sentinel, sentinels, config));
    }

    @RequiredArgsConstructor
    public static class GenericProvider implements IRedisProvider {

        private final JedisPool pool;

        @Override
        public Jedis getResource() {
            return pool.getResource();
        }

        @Override
        public void close() {
            pool.close();
        }
    }

    @RequiredArgsConstructor
    public static class SentinelProvider implements IRedisProvider {

        private final JedisSentinelPool pool;

        @Override
        public Jedis getResource() {
            return pool.getResource();
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
