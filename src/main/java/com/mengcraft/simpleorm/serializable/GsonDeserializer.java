package com.mengcraft.simpleorm.serializable;

import com.google.gson.Gson;
import com.mengcraft.simpleorm.ORM;

import java.util.Map;

public class GsonDeserializer implements IDeserializer {

    public static final GsonDeserializer INSTANCE = new GsonDeserializer();

    @Override
    public Object deserialize(Class<?> cls, Map<String, Object> map) {
        Gson json = ORM.json();
        return json.fromJson(json.toJsonTree(map), cls);
    }
}
