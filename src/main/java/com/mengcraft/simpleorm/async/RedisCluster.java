package com.mengcraft.simpleorm.async;

import com.google.common.collect.Lists;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.RedisWrapper;
import com.mengcraft.simpleorm.lib.Utils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RedisCluster implements ICluster {

    private static final String PATTERN_CH = "sa:%s:ch:%s";// sa:<cluster>:ch:<system>

    /**
     * Integers store next handler id.
     */
    private static final String PATTERN_REFS = "sa:%s:refs";// sa:<cluster>:refs

    /**
     * Sets store all handlers address by category.
     */
    private static final String PATTERN_CAT = "sa:%s:cat:%s";

    /**
     * Hashes store handler address, heartbeat pairs.
     */
    private static final String PATTERN_HEARTBEAT = "sa:%s:hb";

    private static final String SCRIPT_CAT = "REDIS_CLUSTER_cat";
    private static final String SCRIPT_CAT_MULTI = "REDIS_CLUSTER_cat_multi";
    private static final String SCRIPT_CAT_ALL = "REDIS_CLUSTER_cat_all";
    private static final String SCRIPT_HEARTBEAT = "REDIS_CLUSTER_heartbeat";

    private final String cluster;
    private final AtomicInteger mid = new AtomicInteger();

    public RedisCluster(String cluster) {
        this.cluster = cluster;
    }

    @Override
    public void setup(ClusterSystem system) {
        Utils.let(ORM.globalRedisWrapper(), redis -> {
            redis.loads(SCRIPT_CAT, Utils.toString(Utils.getResourceStream("lua/cat.lua")));
            redis.loads(SCRIPT_CAT_MULTI, Utils.toString(Utils.getResourceStream("lua/multicat.lua")));
            redis.loads(SCRIPT_CAT_ALL, Utils.toString(Utils.getResourceStream("lua/catall.lua")));
            redis.loads(SCRIPT_HEARTBEAT, Utils.toString(Utils.getResourceStream("lua/heartbeat.lua")));
            redis.subscribe(String.format(PATTERN_CH, cluster, system.getName()), new MessageBus(system));
        });
        reset(system);
        // close automatic when system close(due to executor close)
        system.executor.scheduleAtFixedRate(heartbeat(system), 8, 8, TimeUnit.SECONDS);
    }

    @Override
    public void reset(ClusterSystem system) {
        // force set heartbeat time
        ORM.globalRedisWrapper().open(jedis -> jedis.hset(String.format(PATTERN_HEARTBEAT, cluster),
                system.getName(),
                String.valueOf(System.currentTimeMillis() / 1000)));
    }

    private Runnable heartbeat(ClusterSystem system) {
        return () -> {
            Number ret = ORM.globalRedisWrapper().eval(SCRIPT_HEARTBEAT, cluster, system.getName());
            if (ret.intValue() != 1) {
                system.reset();
            }
        };
    }

    @Override
    public void close(ClusterSystem system) {
        RedisWrapper redis = ORM.globalRedisWrapper();
        redis.open(jedis -> jedis.hdel(String.format(PATTERN_HEARTBEAT, cluster), system.getName()));
        redis.unsubscribe(String.format(PATTERN_CH, cluster, system.getName()));
    }

    @Override
    public void close(ClusterSystem system, Handler actor) {
        ORM.globalRedisWrapper().open(jedis -> jedis.srem(String.format(PATTERN_CAT, cluster, actor.getCategory()), actor.getAddress()));
    }

    @Override
    public Message send(ClusterSystem system, Handler caller, String address, Object obj, long fid) {
        Message msg = new Message();
        msg.setId(Integer.toUnsignedLong(mid.getAndIncrement()));
        msg.setFutureId(fid);
        msg.setSender(caller.getAddress());
        msg.setReceiver(address);
        if (obj != null) {
            msg.setContents(ORM.json().toJsonTree(obj));
            msg.setContentType(obj.getClass().getName());
        }
        String ch = String.format(PATTERN_CH, cluster, address.substring(0, address.indexOf(':')));
        String json = ORM.json().toJson(msg);
        ORM.globalRedisWrapper().publish(ch, json);
        return msg;
    }

    @Override
    public String randomName(ClusterSystem system) {
        long call = ORM.globalRedisWrapper().call(jedis -> jedis.incr(String.format(PATTERN_REFS, cluster)));
        return Long.toHexString(call);
    }

    @Override
    public void spawn(ClusterSystem system, Handler actor) {
        ORM.globalRedisWrapper().open(jedis -> jedis.sadd(String.format(PATTERN_CAT, cluster, actor.getCategory()), actor.getAddress()));
    }

    @Override
    public List<String> query(ClusterSystem system, Selector selector) {
        List<String> results = Lists.newArrayList();
        switch (selector.getOps()) {
            case ONE:
                results.add(ORM.globalRedisWrapper().eval(SCRIPT_CAT, cluster, selector.getCategory()));
                break;
            case MANY:
                results.addAll(ORM.globalRedisWrapper().eval(SCRIPT_CAT_MULTI,
                        cluster,
                        selector.getCategory(),
                        String.valueOf(selector.getCount())));
                break;
            case ALL:
                results.addAll(ORM.globalRedisWrapper().eval(SCRIPT_CAT_ALL,
                        cluster,
                        selector.getCategory()));
                break;
        }
        return results;
    }
}
