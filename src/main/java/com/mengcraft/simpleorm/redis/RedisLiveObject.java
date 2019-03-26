package com.mengcraft.simpleorm.redis;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@EqualsAndHashCode(exclude = "bucket", callSuper = false)
@Data
@ToString(exclude = "bucket")
public class RedisLiveObject extends AbstractMap<String, String> {// TODO Use lua script to handle some atomic and complex ops.

    private final RedisLiveObjectBucket bucket;
    private final String id;

    RedisLiveObject(RedisLiveObjectBucket bucket, String id) {
        this.bucket = bucket;
        this.id = bucket.getId() + ":" + id;
    }

    /**
     * @return always {@code null} for io
     */
    @Override
    public String remove(Object key) {
        bucket.getRedisWrapper().open(jedis -> jedis.hdel(id, String.valueOf(key)));
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return bucket.getRedisWrapper().call(jedis -> jedis.hexists(id, String.valueOf(key)));
    }

    @Override
    public String get(Object key) {
        return bucket.getRedisWrapper().call(jedis -> jedis.hget(id, String.valueOf(key)));
    }

    /**
     * @return immutable view of entries
     */
    @Override
    public Set<Entry<String, String>> entrySet() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hgetAll(id).entrySet());
    }

    /**
     * @return immutable view of keys
     */
    @Override
    public Set<String> keySet() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hkeys(id));
    }

    @Override
    public int size() {
        return bucket.getRedisWrapper().call(jedis -> jedis.hlen(id).intValue());
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        bucket.getRedisWrapper().open(jedis -> jedis.hmset(id, (Map<String, String>) m));
    }

    /**
     * @return always {@code null} for io
     */
    @Override
    public String put(String key, String value) {
        bucket.getRedisWrapper().open(jedis -> jedis.hset(id, key, value));
        return null;
    }

    /**
     * @return OKAY only if any data update or {@code null} any other wise
     */
    @Override
    public String putIfAbsent(String key, String value) {
        long result = bucket.getRedisWrapper().call(jedis -> jedis.hsetnx(id, key, value));
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
        return bucket.getRedisWrapper().call(jedis -> jedis.hvals(id));
    }

    @Override
    public void clear() {
        bucket.getRedisWrapper().open(jedis -> jedis.del(id));
    }

    public void incrementsValue(String key, int value) {
        bucket.getRedisWrapper().open(jedis -> jedis.hincrBy(id, key, value));
    }

    public void incrementsValue(String key, double value) {
        bucket.getRedisWrapper().open(jedis -> jedis.hincrByFloat(id, key, value));
    }

}
