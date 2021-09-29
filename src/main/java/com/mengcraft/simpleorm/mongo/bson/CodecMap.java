package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;
import com.mengcraft.simpleorm.mongo.bson.jsr310.InstantCodec;
import com.mengcraft.simpleorm.mongo.bson.jsr310.LocalDateCodec;
import com.mengcraft.simpleorm.mongo.bson.jsr310.LocalDateTimeCodec;
import com.mengcraft.simpleorm.mongo.bson.jsr310.LocalTimeCodec;
import org.bson.types.ObjectId;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class CodecMap {

    private static final Map<Class<?>, ICodec> MAP = Maps.newHashMap();

    static {
        // noop
        NoopCodec noopCodec = new NoopCodec();
        MAP.put(UUID.class, noopCodec);
        MAP.put(String.class, noopCodec);
        MAP.put(ObjectId.class, noopCodec);
        // basics
        MAP.put(boolean.class, noopCodec);
        MAP.put(Boolean.class, noopCodec);
        MAP.put(byte.class, new NumberCodec(Number::byteValue));
        MAP.put(Byte.class, new NumberCodec(Number::byteValue));
        MAP.put(short.class, new NumberCodec(Number::shortValue));
        MAP.put(Short.class, new NumberCodec(Number::shortValue));
        MAP.put(int.class, new NumberCodec(Number::intValue));
        MAP.put(Integer.class, new NumberCodec(Number::intValue));
        MAP.put(float.class, new NumberCodec(Number::floatValue));
        MAP.put(Float.class, new NumberCodec(Number::floatValue));
        MAP.put(long.class, new NumberCodec(Number::longValue));
        MAP.put(Long.class, new NumberCodec(Number::longValue));
        MAP.put(double.class, new NumberCodec(Number::doubleValue));
        MAP.put(Double.class, new NumberCodec(Number::doubleValue));
        CharacterCodec characterCodec = new CharacterCodec();
        MAP.put(char.class, characterCodec);
        MAP.put(Character.class, characterCodec);
        // old timestamp and date
        MAP.put(Date.class, noopCodec);
        MAP.put(Timestamp.class, new TimestampCodec());
        // jsr310s
        MAP.put(LocalDateTime.class, new LocalDateTimeCodec());
        MAP.put(LocalDate.class, new LocalDateCodec());
        MAP.put(LocalTime.class, new LocalTimeCodec());
        MAP.put(Instant.class, new InstantCodec());
    }

    public static Object encode(Object obj) {
        return ofCodec(obj.getClass()).encode(obj);
    }

    public static Object decode(Object obj) {
        return ofCodec(obj.getClass()).decode(obj);
    }

    public static ICodec ofCodec(Class<?> type) {
        return MAP.computeIfAbsent(type, CodecMap::asCodec);
    }

    private static ICodec asCodec(Class<?> cls) {
        if (ConfigurationSerializable.class.isAssignableFrom(cls)) {
            return new ConfigurationSerializableCodec(cls);
        }
        if (Map.class.isAssignableFrom(cls)) {
            return new MapCodec(cls);
        }
        if (Collection.class.isAssignableFrom(cls)) {
            return new CollectionCodec(cls);
        }
        if (cls.isEnum()) {
            return new EnumCodec(cls);
        }
        return new PojoCodec(cls);
    }
}
