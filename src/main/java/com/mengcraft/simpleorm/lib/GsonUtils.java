package com.mengcraft.simpleorm.lib;

import com.google.common.collect.Maps;
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
import com.mengcraft.simpleorm.serializable.CustomTypeAdapter;
import com.mengcraft.simpleorm.serializable.GsonDeserializer;
import com.mengcraft.simpleorm.serializable.IDeserializer;
import com.mengcraft.simpleorm.serializable.SerializableTypes;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mengcraft.simpleorm.ORM.nil;

public class GsonUtils {

    private static final Field PRIMITIVE_VALUE = Utils.getAccessibleField(JsonPrimitive.class, "value");
    private static TypeFunctionRegistry<Object> registry = new TypeFunctionRegistry<>();

    static {
        registry.register(JsonNull.class, e -> null);
        registry.register(JsonPrimitive.class, e -> {
            try {
                return PRIMITIVE_VALUE.get(e);
            } catch (IllegalAccessException ignored) {
            }
            return null;
        });
        registry.register(JsonArray.class, e -> StreamSupport.stream(e.spliterator(), false).map(registry::handle).collect(Collectors.toList()));
        registry.register(JsonObject.class, e -> {
            Map<String, Object> container = Maps.newHashMap();
            for (Map.Entry<String, JsonElement> node : e.entrySet()) {
                container.put(node.getKey(), registry.handle(node.getValue()));
            }
            return container;
        });
    }

    public static Object dump(JsonElement value) {
        return registry.handle(value);
    }

    public static Gson createJsonInBuk() {
        return createJsonInBuk(null);
    }

    public static Gson createJsonInBuk(FieldNamingPolicy policy) {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapterFactory(CustomTypeAdapter.newTypeHierarchyFactory(ConfigurationSerializable.class, new CustomSerializer()));
        b.registerTypeAdapterFactory(CustomTypeAdapter.newTypeHierarchyFactory(Collection.class, new MultilineTextDeserializer()));
        // jsr310s
        b.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer());
        b.registerTypeAdapter(LocalDate.class, new LocalDateSerializer());
        b.registerTypeAdapter(LocalTime.class, new LocalTimeSerializer());
        b.registerTypeAdapter(Instant.class, new InstantSerializer());
        if (!nil(policy)) {
            b.setFieldNamingPolicy(policy);
        }
        return b.create();
    }

    public static class CustomSerializer implements JsonSerializer<ConfigurationSerializable>, JsonDeserializer<ConfigurationSerializable> {

        public JsonElement serialize(ConfigurationSerializable input, Type clz, JsonSerializationContext ctx) {
            return ctx.serialize(input.serialize());
        }

        public ConfigurationSerializable deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            if (!element.isJsonObject()) {
                return null;
            }
            Class<ConfigurationSerializable> cls = (Class<ConfigurationSerializable>) type;
            IDeserializer deserializer = SerializableTypes.asDeserializer(cls);
            if (deserializer == GsonDeserializer.INSTANCE) {// delegate to defaults
                return null;
            }
            return (ConfigurationSerializable) deserializer.deserialize(cls, (Map<String, Object>) dump(element));
        }
    }

    public static class LocalDateTimeSerializer implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return LocalDateTime.parse(json.getAsString());
            }
            throw new JsonParseException(String.format("%s cannot parsed to LocalDateTime", json));
        }

        @Override
        public JsonElement serialize(LocalDateTime src, Type type, JsonSerializationContext context) {
            return context.serialize(src.toString());
        }
    }

    public static class LocalDateSerializer implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return LocalDate.parse(json.getAsString());
            }
            throw new JsonParseException(String.format("%s cannot parsed to LocalDate", json));
        }

        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.toString());
        }
    }

    public static class LocalTimeSerializer implements JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {

        @Override
        public LocalTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return LocalTime.parse(json.getAsString());
            }
            throw new JsonParseException(String.format("%s cannot parsed to LocalTime", json));
        }

        @Override
        public JsonElement serialize(LocalTime src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.toString());
        }
    }

    public static class InstantSerializer implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return Instant.ofEpochMilli(json.getAsLong());
            }
            throw new JsonParseException(String.format("%s cannot parsed to Instant", json));
        }

        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.toEpochMilli());
        }
    }
}
