package com.mengcraft.simpleorm.serializable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mengcraft.simpleorm.lib.GsonUtils;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.Type;
import java.util.Map;

public class ConfigurationSerializableAdapter implements JsonSerializer<ConfigurationSerializable>, JsonDeserializer<ConfigurationSerializable> {

    @Override
    public ConfigurationSerializable deserialize(JsonElement element, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (!element.isJsonObject()) {
            throw new JsonParseException("Expected object, got " + element);
        }
        Class<ConfigurationSerializable> cls = (Class<ConfigurationSerializable>) type;
        IDeserializer deserializer = SerializableTypes.asDeserializer(cls);
        if (deserializer == GsonDeserializer.INSTANCE) {
            // bypass to next Adapter
            return ((DelegatedTypeAdapter<ConfigurationSerializable>.GsonContextImpl) context).delegated(element);
        }
        return (ConfigurationSerializable) deserializer.deserialize(cls, (Map<String, Object>) GsonUtils.dump(element));
    }

    @Override
    public JsonElement serialize(ConfigurationSerializable serializable, Type type, JsonSerializationContext context) {
        return context.serialize(serializable.serialize());
    }
}
