package com.mengcraft.simpleorm.serializable;

import com.google.gson.Gson;
import com.mengcraft.simpleorm.ORM;

import java.util.Map;

public class GsonDeserializer<T> implements IDeserializer<T> {

    public static final GsonDeserializer<?> INSTANCE = new GsonDeserializer<>();

    @Override
    public T deserialize(Class<T> cls, Map<String, ?> map) {
        Gson json = ORM.json();
        return json.fromJson(json.toJsonTree(map), cls);
    }
}
