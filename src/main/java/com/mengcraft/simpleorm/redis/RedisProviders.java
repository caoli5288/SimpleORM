package com.mengcraft.simpleorm.redis;

import com.mengcraft.simpleorm.lib.Utils;
import com.mengcraft.simpleorm.provider.IRedisProvider;
import lombok.RequiredArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RedisProviders {

    public static IRedisProvider of(String sentinel, String url, int conn, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        if (conn >= 1) {
            config.setMaxTotal(conn);
        }
        if (Utils.isNullOrEmpty(sentinel)) {
            URI remote = URI.create(url);
            return new GenericProvider(Utils.isNullOrEmpty(password) ?
                    new JedisPool(config, remote) :
                    new JedisPool(config, remote.getHost(), remote.getPort(), 2000, password));
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
        return new SentinelProvider(Utils.isNullOrEmpty(password) ?
                new JedisSentinelPool(sentinel, sentinels, config) :
                new JedisSentinelPool(sentinel, sentinels, config, password));
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
