package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;
import com.mengcraft.simpleorm.lib.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.SneakyThrows;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class MapCodec implements ICodec {

    private static final Map<Class<?>, Class<?>> MAPS = Maps.newHashMap();

    static {
        MAPS.put(Map.class, HashMap.class);
        MAPS.put(ConcurrentMap.class, ConcurrentHashMap.class);
        MAPS.put(ConcurrentNavigableMap.class, ConcurrentSkipListMap.class);
        MAPS.put(NavigableMap.class, TreeMap.class);
        MAPS.put(SortedMap.class, TreeMap.class);
    }

    private final Constructor<?> constructor;

    @SneakyThrows
    public MapCodec(Class<?> mapCls) {
        constructor = Utils.getAccessibleConstructor(MAPS.getOrDefault(mapCls, mapCls));
    }

    @Override
    public Object encode(Object to) {
        Map<String, Object> map = (Map<String, Object>) to;
        DBObject obj = new BasicDBObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            obj.put(entry.getKey(), CodecMap.encode(entry.getValue()));
        }
        return obj;
    }

    @Override
    public Object decode(Object from) {
        Map<String, Object> map = newMap();
        Map<String, Object> obj = (Map<String, Object>) from;
        for (String s : obj.keySet()) {
            map.put(s, CodecMap.decode(obj.get(s)));
        }
        return map;
    }

    @SneakyThrows
    private Map<String, Object> newMap() {
        return (Map<String, Object>) constructor.newInstance();
    }
}
