package com.mengcraft.simpleorm.redis;

import com.mengcraft.simpleorm.RedisWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class RedisLiveObjectBucket {

    private final RedisWrapper redisWrapper;
    private final String id;

    public RedisLiveObject get(String obj) {
        return new RedisLiveObject(this, obj);
    }

    public void remove(String object) {
        redisWrapper.open(jedis -> jedis.del(id + ":" + object));
    }

    public boolean contains(String obj) {
        return redisWrapper.call(jedis -> jedis.exists(id + ":" + obj));
    }

    /**
     * @deprecated in develop
     */
    public RedisLiveList getList(String id) {
        return new RedisLiveList(this, id);
    }
}
