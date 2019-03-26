package com.mengcraft.simpleorm.redis;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mengcraft.simpleorm.ORM;
import com.mengcraft.simpleorm.RedisWrapper;
import com.mengcraft.simpleorm.lib.VarIntDataStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class RedisMessageTopic {

    private final RedisWrapper redisWrapper;
    private final String name;
    private final Map<String, PackagedListener<?>> map = new HashMap<>();
    private Consumer<byte[]> consumer;

    public <T> void addListener(Class<T> clazz, MessageTopicListener<T> listener) {
        if (map.isEmpty()) {// 1st subscribe channel
            if (consumer == null) {
                consumer = this::receive;
            }
            redisWrapper.subscribe("simple_topic:" + name, consumer);
        }
        map.put(clazz.getName(), new PackagedListener<>(clazz, listener));
    }

    private void cleanup() {
        if (map.isEmpty()) {
            redisWrapper.unsubscribe("simple_topic:" + name, consumer);
        }
    }

    public void removeAll() {
        if (map.isEmpty()) {
            return;
        }
        map.clear();
        cleanup();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean removeListener(Class clazz) {
        if (map.containsKey(clazz.getName())) {
            map.remove(clazz.getName());
            cleanup();
            return true;
        }
        return false;
    }

    @SneakyThrows
    protected void receive(byte[] data) {
        ByteArrayDataInput buf = ByteStreams.newDataInput(data);
        String clazzName = VarIntDataStream.readString(buf);
        if (map.containsKey(clazzName)) {
            PackagedListener<?> packaged = map.get(clazzName);
            Object obj = ORM.deserialize(packaged.clazz, ((Map<String, Object>) JSONValue.parse(VarIntDataStream.readString(buf))));
            packaged.handle(name, obj);
        }
    }

    public void publish(Object obj) {
        ByteArrayDataOutput buf = ByteStreams.newDataOutput();
        VarIntDataStream.writeString(buf, obj.getClass().getName());
        VarIntDataStream.writeString(buf, JSONObject.toJSONString(ORM.serialize(obj)));
        redisWrapper.publish("simple_topic:" + name, buf.toByteArray());
    }

    public interface MessageTopicListener<T> {

        void handle(String topic, T obj);
    }

    @Data
    private class PackagedListener<T> {

        private final Class<T> clazz;
        private final MessageTopicListener<T> listener;

        protected void handle(String topic, Object obj) {
            listener.handle(topic, (T) obj);
        }
    }

}
