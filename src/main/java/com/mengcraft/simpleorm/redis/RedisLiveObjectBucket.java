package com.mengcraft.simpleorm.redis;

import com.mengcraft.simpleorm.RedisWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class RedisLiveObjectBucket {

    private final RedisWrapper redisWrapper;
    private final String id;

    public RedisLiveObject get(String object) {
        return new RedisLiveObject(this, object);
    }

    public void remove(String object) {
        redisWrapper.open(jedis -> jedis.del(id + ":" + object));
    }
}
