package com.mengcraft.simpleorm.lib;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.val;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mengcraft.simpleorm.ORM.nil;
import static com.mengcraft.simpleorm.lib.Tuple.tuple;

public class JsonHelper {

    private static final FunctionRegistry<JsonElement, Object> $ = new FunctionRegistry<>();

    static {
        $.register(JsonNull.class, jsonElement -> null);
        $.register(JsonPrimitive.class, jsonElement -> Reflector.getField(jsonElement, "value"));
        $.register(JsonObject.class, jsonElement -> {
            JsonObject object = jsonElement.getAsJsonObject();
            Map<String, Object> container = new HashMap<>(object.size());
            dump(object, container);
            return container;
        });
        $.register(JsonArray.class, jsonElement -> {
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
        return $.handle(value.getClass(), value);
    }

    public static void dump(JsonArray jsonArray, List<Object> container) {
        for (JsonElement jsonElement : jsonArray) {
            Object primitive = primitive(jsonElement);
            if (!nil(primitive)) {
                container.add(primitive);
            }
        }
    }

    public static Gson createJsonInBuk() {
        return createJsonInBuk(null);
    }

    public static Gson createJsonInBuk(FieldNamingPolicy policy) {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeHierarchyAdapter(ConfigurationSerializable.class, new JsonSerializeAdapter());
        if (!nil(policy)) {
            b.setFieldNamingPolicy(policy);
        }
        return b.create();
    }

    public static class JsonSerializeAdapter implements JsonSerializer<ConfigurationSerializable>, JsonDeserializer<ConfigurationSerializable> {

        public JsonElement serialize(ConfigurationSerializable input, Type clz, JsonSerializationContext ctx) {
            return ctx.serialize(input.serialize());
        }

        public ConfigurationSerializable deserialize(JsonElement jsonElement, Type clz, JsonDeserializationContext ctx) throws JsonParseException {
            if (!jsonElement.isJsonObject()) {
                return null;
            }
            val tuple = tuple(Map.class, primitive(jsonElement));
            try {
                return Reflector.object((Class<ConfigurationSerializable>) clz, tuple);
            } catch (Exception ignored) {
            }
            return Reflector.invoke(clz, "deserialize", tuple);
        }
    }

}
