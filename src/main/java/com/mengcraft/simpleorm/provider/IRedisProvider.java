package com.mengcraft.simpleorm.provider;

import redis.clients.jedis.Jedis;

import java.io.Closeable;

public interface IRedisProvider extends Closeable {

    Jedis getResource();
}
