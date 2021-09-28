package com.mengcraft.simpleorm.mongo.bson;

import com.mengcraft.simpleorm.serializable.IDeserializer;
import com.mengcraft.simpleorm.serializable.SerializableTypes;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.Map;

public class ConfigurationSerializableCodec implements ICodec {

    private final Class<?> cls;
    private final IDeserializer deserializer;

    public ConfigurationSerializableCodec(Class<?> cls) {
        this.cls = cls;
        deserializer = SerializableTypes.asDeserializer(cls);
    }

    @Override
    public Object encode(Object to) {
        Map<String, Object> map = ((ConfigurationSerializable) to).serialize();
        return CodecMap.ofCodec(Map.class).encode(map);
    }

    @Override
    public Object decode(Object from) {
        Map<String, Object> map = (Map<String, Object>) CodecMap.ofCodec(Map.class).decode(from);
        return deserializer.deserialize(cls, map);
    }
}
