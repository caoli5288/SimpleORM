package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;
import org.bson.types.ObjectId;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class CodecMap {

    private static final Map<Class<?>, ICodec> MAP = Maps.newHashMap();

    static {
        MAP.put(byte.class, NoopCodec.getInstance());
        MAP.put(char.class, NoopCodec.getInstance());
        MAP.put(int.class, NoopCodec.getInstance());
        MAP.put(long.class, NoopCodec.getInstance());
        MAP.put(float.class, NoopCodec.getInstance());
        MAP.put(double.class, NoopCodec.getInstance());
        MAP.put(UUID.class, NoopCodec.getInstance());
        MAP.put(String.class, NoopCodec.getInstance());
        MAP.put(ObjectId.class, NoopCodec.getInstance());
        MAP.put(boolean.class, NoopCodec.getInstance());
        MAP.put(Boolean.class, NoopCodec.getInstance());
        CharacterCodec characterCodec = new CharacterCodec();
        MAP.put(char.class, characterCodec);
        MAP.put(Character.class, characterCodec);
    }

    public static Object encode(Object obj) {
        return ofCodec(obj.getClass()).encode(obj);
    }

    public static Object decode(Object obj) {
        return ofCodec(obj.getClass()).decode(obj);
    }

    public static ICodec ofCodec(Class<?> type) {
        if (MAP.containsKey(type)) {
            return MAP.get(type);
        }
        // We don't use compute* because it causes exceptions
        ICodec asCodec = asCodec(type);
        MAP.put(type, asCodec);
        return asCodec;
    }

    private static ICodec asCodec(Class<?> cls) {
        if (Number.class.isAssignableFrom(cls)) {
            return NoopCodec.getInstance();
        }
        if (ConfigurationSerializable.class.isAssignableFrom(cls)) {
            return new ConfigurationSerializableCodec(cls);
        }
        if (Map.class.isAssignableFrom(cls)) {
            return new MapCodec(cls);
        }
        if (Collection.class.isAssignableFrom(cls)) {
            return new CollectionCodec(cls);
        }
        return new PojoCodec(cls);
    }
}
