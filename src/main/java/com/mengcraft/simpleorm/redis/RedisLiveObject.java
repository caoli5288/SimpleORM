package com.mengcraft.simpleorm.redis;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class RedisLiveObject extends AbstractMap<String, String> {// TODO Use lua script to handle some atomic and complex ops.

    private final RedisLiveObjectBucket bucket;
    private final String id;
    private final String objectId = bucket.getId() + ":" + id;

    /**
     * @return always {@code null} for io
     */
    @Override
    public String remove(Object key) {
        bucket.getRedisWrapper().open(jedis -> jedis.hdel(objectId, String.valueOf(key)));
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return bucket.getRedisWrapper().call(jedis -> jedis.hexists(objectId, String.valueOf(key)));
    }

    @Override
    public String get(Object key) {
        return bucket.getRedisWrapper().call(jedis -> jedis.hget(objectId, String.valueOf(key)));
    }

    /**
     * @return immutable view of entries
     */
    @Override
    public Set<Entry<String, String>> entrySet() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hgetAll(objectId).entrySet());
    }

    /**
     * @return immutable view of keys
     */
    @Override
    public Set<String> keySet() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hkeys(objectId));
    }

    @Override
    public int size() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hlen(objectId).intValue());
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        bucket.getRedisWrapper().open(jedis -> jedis.hmset(objectId, (Map<String, String>) m));
    }

    /**
     * @return always {@code null} for io
     */
    @Override
    public String put(String key, String value) {
        bucket.getRedisWrapper().open(jedis -> jedis.hset(objectId, key, value));
        return null;
    }

    /**
     * @return OKAY only if any data update or {@code null} any other wise
     */
    @Override
    public String putIfAbsent(String key, String value) {
        long result = bucket.getRedisWrapper().call(jedis -> jedis.hsetnx(objectId, key, value));
        if (result == 1) {
            return "OKAY";
        }
        return null;
    }

    /**
     * @return immutable view of values
     */
    @Override
    public Collection<String> values() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hvals(objectId));
    }

    @Override
    public void clear() {
        bucket.getRedisWrapper().open(jedis -> jedis.del(objectId));
    }

}
