package com.mengcraft.simpleorm;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mengcraft.simpleorm.lib.Utils;
import com.mengcraft.simpleorm.provider.IRedisProvider;
import com.mengcraft.simpleorm.redis.RedisLiveObjectBucket;
import com.mengcraft.simpleorm.redis.RedisMessageTopic;
import lombok.Cleanup;
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
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.mengcraft.simpleorm.ORM.nil;

public class RedisWrapper implements Closeable {

    private final IRedisProvider resources;
    private MessageFilter filter;

    public RedisWrapper(IRedisProvider resources) {
        this.resources = resources;
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
        subscribe(channel, consumer, command -> new Thread(command).start());
    }

    public synchronized void subscribe(String channel, Consumer<byte[]> consumer, Executor executor) {
        if (nil(filter)) {
            filter = new MessageFilter();
            Jedis client = resources.getResource();
            client.subscribe(filter, new byte[0]);// hacked
            filter.addSubscriber(channel, consumer);
            executor.execute(() -> {
                try {
                    filter.execute();
                } finally {
                    client.close();
                }
            });
        } else if (!filter.isSubscribed(channel, consumer)) {
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

        if (filter.subscribers.removeAll(channel).isEmpty()) {// no op
            return;
        }

        filter.unsubscribe(channel.getBytes(StandardCharsets.UTF_8));
        if (!filter.subscribers.isEmpty()) {
            return;
        }

        filter = null;
    }

    public synchronized void unsubscribe(String channel, Consumer<byte[]> consumer) {
        if (nil(filter)) {
            return;
        }

        int subscribers = filter.removeSubscriber(channel, consumer);
        if (subscribers == 0) {
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

    private static class MessageFilter extends BinaryJedisPubSub {

        private static final Method METHOD_process = Utils.getAccessibleMethod(BinaryJedisPubSub.class, "process", Client.class);
        private static final Field FIELD_client = Utils.getAccessibleField(BinaryJedisPubSub.class, "client");

        private final Multimap<String, Consumer<byte[]>> subscribers = HashMultimap.create();

        void addSubscriber(String channel, Consumer<byte[]> consumer) {
            if (!subscribers.containsKey(channel)) {
                subscribe(channel.getBytes(StandardCharsets.UTF_8));
            }
            subscribers.put(channel, consumer);
        }

        int removeSubscriber(String channel, Consumer<byte[]> consumer) {
            if (subscribers.remove(channel, consumer) && !subscribers.containsKey(channel)) {
                unsubscribe(channel.getBytes(StandardCharsets.UTF_8));
            }
            return subscribers.size();
        }

        public void onMessage(byte[] channel, byte[] message) {
            Collection<Consumer<byte[]>> allConsumer = subscribers.get(new String(channel, StandardCharsets.UTF_8));
            if (allConsumer.isEmpty()) {
                return;
            }

            for (Consumer<byte[]> consumer : allConsumer) consumer.accept(message);
        }

        @Override
        @SneakyThrows
        public void proceed(Client client, byte[]... channels) {
            FIELD_client.set(this, client);
        }

        @SneakyThrows
        void execute() {
            Client client = (Client) FIELD_client.get(this);
            client.setTimeoutInfinite();
            try {
                METHOD_process.invoke(this, client);
            } finally {
                client.rollbackTimeout();
            }
        }

        public boolean isSubscribed(String channel, Consumer<byte[]> consumer) {
            return subscribers.containsEntry(channel, consumer);
        }
    }

}
