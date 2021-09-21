package com.mengcraft.simpleorm;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.mengcraft.simpleorm.lib.Utils;
import com.mengcraft.simpleorm.provider.IRedisProvider;
import com.mengcraft.simpleorm.redis.RedisLiveObjectBucket;
import com.mengcraft.simpleorm.redis.RedisMessageTopic;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.SetParams;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.mengcraft.simpleorm.ORM.nil;

public class RedisWrapper implements Closeable {

    private final Map<String, String> scripts = Maps.newHashMap();
    private final IRedisProvider resources;
    private MessageFilter filter;

    public RedisWrapper(IRedisProvider resources) {
        this.resources = resources;
    }

    public boolean loads(String scriptName, String contents) {
        if (scripts.containsKey(scriptName)) {
            return false;
        }
        synchronized (scripts) {
            if (scripts.containsKey(scriptName)) {
                return false;
            }
            scripts.put(scriptName, call(jedis -> jedis.scriptLoad(contents)));
            return true;
        }
    }


    public <T> T eval(String scriptName, String... args) {
        return eval(0, scriptName, args);
    }

    @SuppressWarnings("unchecked")
    public <T> T eval(int database, String scriptName, String... args) {
        String sha = Objects.requireNonNull(scripts.get(scriptName), "script not found");
        return (T) call(database, jedis -> jedis.evalsha(sha, 0, args));
    }

    @Override
    @SneakyThrows
    public void close() {
        resources.close();
    }

    public String ping() throws JedisConnectionException {
        return call(jd -> jd.ping());
    }

    public <T> T call(Function<Jedis, T> function) {
        @Cleanup Jedis jedis = resources.getResource();
        return function.apply(jedis);
    }

    public <T> T call(int database, Function<Jedis, T> function) {
        Preconditions.checkArgument(database > -1);
        if (database == 0) {
            return call(function);
        }
        T obj;
        Jedis jedis = resources.getResource();
        try {
            jedis.select(database);
            obj = function.apply(jedis);
        } finally {
            jedis.select(0);
            jedis.close();
        }
        return obj;
    }

    public void publish(String channel, String message) {
        publish(channel, message.getBytes(StandardCharsets.UTF_8));
    }

    public void publish(String channel, byte[] message) {
        open(client -> client.publish(channel.getBytes(StandardCharsets.UTF_8), message));
    }

    public void open(Consumer<Jedis> consumer) {
        @Cleanup Jedis jedis = resources.getResource();
        consumer.accept(jedis);
    }

    public void open(int database, Consumer<Jedis> consumer) {
        Preconditions.checkArgument(database > -1);
        if (database == 0) {
            open(consumer);
        } else {
            Jedis jedis = resources.getResource();
            try {
                jedis.select(database);
                consumer.accept(jedis);
            } finally {
                jedis.select(0);
                jedis.close();
            }
        }
    }

    public void subscribe(String channel, Consumer<byte[]> consumer) {
        subscribe(channel, consumer, command -> new Thread(command, "REDIS-WRAPPER-SUBSCRIBER").start());
    }

    synchronized void subscribe(String channel, Consumer<byte[]> consumer, Executor executor) {
        if (nil(filter)) {
            /*
             * Ugly workaround for jedis async subscribe bug.
             */
            filter = new MessageFilter(executor);
            filter.addSubscriber(channel, consumer);
            filter.execute();
            return;
        }
        if (!filter.isSubscribed(channel, consumer)) {
            filter.addSubscriber(channel, consumer);
        }
    }

    public synchronized void unsubscribeAll() {
        if (nil(filter)) {
            return;
        }
        filter.unsubscribe();
        filter = null;
    }

    @SneakyThrows
    public synchronized void unsubscribe(String channel) {
        if (nil(filter)) {
            return;
        }

        if (filter.consumers.removeAll(channel).isEmpty()) {// no op
            return;
        }

        filter.unsubscribe(channel.getBytes(StandardCharsets.UTF_8));
        if (!filter.consumers.isEmpty()) {
            return;
        }

        filter = null;
    }

    public synchronized void unsubscribe(String channel, Consumer<byte[]> consumer) {
        if (nil(filter)) {
            return;
        }

        filter.removeSubscriber(channel, consumer);
        if (filter.isEmpty()) {
            filter = null;
        }
    }

    public void set(String key, Object obj) {
        set(key, obj, 0);
    }

    public void set(String key, Object obj, int expire) {
        open(jedis -> {
            String data = (obj instanceof String) ? obj.toString() : JSONObject.toJSONString(ORM.serialize(obj));
            if (expire >= 1) {
                jedis.set(key, data, SetParams.setParams().ex(expire));
                return;
            }
            jedis.set(key, data);
        });
    }

    public String getString(String key) {
        return call(jedis -> jedis.get(key));
    }

    public <T> T get(String key, Class<T> clazz) {
        return call(jedis -> {
            String str = jedis.get(key);
            if (str == null) {
                return null;
            }
            return ORM.deserialize(clazz, (Map<String, Object>) JSONValue.parse(str));
        });
    }

    public void del(String key) {
        open(jedis -> jedis.del(key));
    }

    public RedisMessageTopic getMessageTopic(String topic) {
        return new RedisMessageTopic(this, topic);
    }

    public RedisLiveObjectBucket getLiveObjectBucket(String bucket) {
        return new RedisLiveObjectBucket(this, bucket);
    }

    private static final Method METHOD_PUB_SUB_process = Utils.getAccessibleMethod(BinaryJedisPubSub.class, "process", Client.class);
    private static final Field FIELD_PUB_SUB_client = Utils.getAccessibleField(BinaryJedisPubSub.class, "client");

    @RequiredArgsConstructor
    class MessageFilter extends BinaryJedisPubSub {

        private final Multimap<String, Consumer<byte[]>> consumers = HashMultimap.create();
        private final Executor executor;
        private boolean receiving;

         synchronized void addSubscriber(String channel, Consumer<byte[]> consumer) {
            if (receiving && !consumers.containsKey(channel)) {
                subscribe(channel.getBytes(StandardCharsets.UTF_8));
            }
            consumers.put(channel, consumer);
        }

        synchronized void removeSubscriber(String channel, Consumer<byte[]> consumer) {
            if (consumers.remove(channel, consumer) && !consumers.containsKey(channel)) {
                unsubscribe(channel.getBytes(StandardCharsets.UTF_8));
            }
        }

        boolean isEmpty() {
            return consumers.isEmpty();
        }

        public void onMessage(byte[] channel, byte[] message) {
            Collection<Consumer<byte[]>> allConsumer = consumers.get(new String(channel, StandardCharsets.UTF_8));
            if (allConsumer.isEmpty()) {
                return;
            }

            for (Consumer<byte[]> consumer : allConsumer) consumer.accept(message);
        }

        @Override
        @SneakyThrows
        public void proceed(Client client, byte[]... channels) {
            FIELD_PUB_SUB_client.set(this, client);
        }

        void execute() {
            executor.execute(() -> {
                while (!consumers.isEmpty()) {
                    Jedis client = resources.getResource();
                    client.subscribe(this, new byte[0]);// hacked
                    // then set this state to true
                    synchronized (this) {
                        receiving = true;
                        for (String s : consumers.keySet()) {
                            subscribe(s.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    try {
                        exec();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        receiving = false;
                        client.close();
                    }
                }
            });
        }

        @SneakyThrows
        void exec() {
            Client client = (Client) FIELD_PUB_SUB_client.get(this);
            client.setTimeoutInfinite();
            try {
                METHOD_PUB_SUB_process.invoke(this, client);
            } finally {
                client.rollbackTimeout();
            }
        }

        public boolean isSubscribed(String channel, Consumer<byte[]> consumer) {
            return consumers.containsEntry(channel, consumer);
        }
    }

}
