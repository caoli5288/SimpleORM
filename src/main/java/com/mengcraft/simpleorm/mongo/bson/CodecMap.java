package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;
import org.bson.types.ObjectId;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class CodecMap {

    private static final Map<Class<?>, ICodec> MAP = Maps.newHashMap();

    static {
        MAP.put(Date.class, NoopCodec.getInstance());
        MAP.put(UUID.class, NoopCodec.getInstance());
        MAP.put(String.class, NoopCodec.getInstance());
        MAP.put(ObjectId.class, NoopCodec.getInstance());
        CharacterCodec characterCodec = new CharacterCodec();
        MAP.put(char.class, characterCodec);
        MAP.put(Character.class, characterCodec);
    }

    static Object encode(Object obj) {
        return ofCodec(obj.getClass()).encode(obj);
    }

    static Object decode(Object obj) {
        return ofCodec(obj.getClass()).decode(obj);
    }

    public static ICodec ofCodec(Class<?> type) {
        return MAP.computeIfAbsent(type, asCodec());
    }

    private static Function<Class<?>, ICodec> asCodec() {
        return cls -> {
            if (cls.isAssignableFrom(Number.class)) {
                return NoopCodec.getInstance();
            }
            if (cls.isAssignableFrom(ConfigurationSerializable.class)) {
                return new ConfigurationSerializableCodec(cls);
            }
            // TODO
            return null;
        };
    }
}
