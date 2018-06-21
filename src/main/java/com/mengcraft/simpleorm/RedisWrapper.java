package com.mengcraft.simpleorm;

import lombok.Cleanup;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.function.Consumer;

public class RedisWrapper {

    private JedisPool pool;

    public RedisWrapper(String url, GenericObjectPoolConfig config) {
        pool = new JedisPool(config, URI.create(url));
    }

    public void open(Consumer<Jedis> consumer) {
        @Cleanup Jedis jedis = pool.getResource();
        consumer.accept(jedis);
    }

    public void publish(String channel, String message) {
        open(client -> client.publish(channel, message));
    }

    public void publish(String channel, byte[] message) {
        open(client -> client.publish(channel.getBytes(Charset.forName("utf8")), message));
    }

    // TODO Impl subscribe adapter
}
