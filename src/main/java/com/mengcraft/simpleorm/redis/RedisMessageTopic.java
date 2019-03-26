package com.mengcraft.simpleorm.redis;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.RedisWrapper;
import com.mengcraft.simpleorm.lib.Tuple;
import com.mengcraft.simpleorm.lib.VarIntDataStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.Map;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class RedisMessageTopic {

    private final RedisWrapper redisWrapper;
    private final String name;
    private final Multimap<String, Tuple<String, MessageTopicListener>> multimap = HashMultimap.create();// <class_name, (plugin_name, listener)>
    private Consumer<byte[]> consumer;

    public <T> void addListener(Plugin plugin, Class<T> clazz, MessageTopicListener<T> listener) {
        if (multimap.isEmpty()) {// 1st subscribe channel
            if (consumer == null) {
                consumer = this::receive;
            }
            redisWrapper.subscribe("simple_topic:" + name, consumer);
        }
        multimap.put(clazz.getName(), Tuple.tuple(plugin.getName(), listener));
    }

    public boolean removeListener(Plugin plugin, Class clazz) {
        if (multimap.containsKey(clazz.getName()) && multimap.get(clazz.getName()).removeIf(p -> p.left().equals(plugin.getName()))) {
            cleanup();
            return true;
        }
        return false;
    }

    private void cleanup() {
        if (multimap.isEmpty()) {
            redisWrapper.unsubscribe("simple_topic:" + name, consumer);
        }
    }

    public void removeAll() {
        if (multimap.isEmpty()) {
            return;
        }
        multimap.clear();
        cleanup();
    }

    public boolean isEmpty() {
        return multimap.isEmpty();
    }

    public boolean removeListener(Plugin plugin) {
        if (!multimap.isEmpty() && multimap.values().removeIf(p -> p.left().equals(plugin.getName()))) {
            cleanup();
            return true;
        }
        return false;
    }

    public boolean removeListener(Class clazz) {
        if (!multimap.isEmpty() && !multimap.removeAll(clazz.getName()).isEmpty()) {
            cleanup();
            return true;
        }
        return false;
    }

    @SneakyThrows
    protected void receive(byte[] data) {
        ByteArrayDataInput buf = ByteStreams.newDataInput(data);
        String clazzName = VarIntDataStream.readString(buf);
        if (multimap.containsKey(clazzName)) {
            Class<?> clazz = Class.forName(clazzName);
            Object obj = ORM.deserialize(clazz, ((Map<String, Object>) JSONValue.parse(VarIntDataStream.readString(buf))));
            for (Tuple<String, MessageTopicListener> l : multimap.get(clazzName)) {
                l.right().handle(name, obj);
            }
        }
    }

    public void publish(Object obj) {
        ByteArrayDataOutput buf = ByteStreams.newDataOutput();
        VarIntDataStream.writeString(buf, obj.getClass().getName());
        VarIntDataStream.writeString(buf, JSONObject.toJSONString(ORM.serialize(obj)));
        redisWrapper.publish(name, buf.toByteArray());
    }

    public interface MessageTopicListener<T> {

        void handle(String topic, T obj);
    }

}
