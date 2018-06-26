package com.mengcraft.simpleorm.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mengcraft.simpleorm.ORM.nil;

public class JsonHelper {

    private static final FunctionRegistry<JsonElement, Object> _FUN = new FunctionRegistry<>();

    static {
        _FUN.register(JsonNull.class, jsonElement -> null);
        _FUN.register(JsonPrimitive.class, jsonElement -> Reflector.getField(jsonElement, "value"));
        _FUN.register(JsonObject.class, jsonElement -> {
            JsonObject object = jsonElement.getAsJsonObject();
            Map<String, Object> container = new HashMap<>(object.size());
            dump(object, container);
            return container;
        });
        _FUN.register(JsonArray.class, jsonElement -> {
            JsonArray array = jsonElement.getAsJsonArray();
            List<Object> container = new ArrayList<>(array.size());
            dump(array, container);
            return container;
        });
    }

    public static void dump(JsonObject object, Map<String, Object> container) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Object primitive = primitive(entry.getValue());
            if (!nil(primitive)) {
                container.put(entry.getKey(), primitive);
            }
        }
    }

    public static Object primitive(JsonElement value) {
        return _FUN.handle(value.getClass(), value);
    }

    public static void dump(JsonArray jsonArray, List<Object> container) {
        for (JsonElement jsonElement : jsonArray) {
            Object primitive = primitive(jsonElement);
            if (!nil(primitive)) {
                container.add(primitive);
            }
        }
    }

}
