package com.mengcraft.simpleorm.mongo.bson;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.SneakyThrows;

import java.util.Map;

public class MapCodec implements ICodec {

    private final Class<?> mapCls;

    public MapCodec(Class<?> mapCls) {
        // TODO
        this.mapCls = mapCls;
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
        DBObject obj = (DBObject) from;
        for (String s : obj.keySet()) {
            map.put(s, CodecMap.decode(obj.get(s)));
        }
        return map;
    }

    @SneakyThrows
    private Map<String, Object> newMap() {
        // TODO
        return (Map<String, Object>) mapCls.newInstance();
    }
}
