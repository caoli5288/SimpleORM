package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class BsonCodecs {

    private static final Map<Class<?>, IBsonCodec> CODECS = Maps.newHashMap();

    static {
        NoopCodec noopCodec = new NoopCodec();
        CODECS.put(String.class, noopCodec);
        CODECS.put(Byte.class, noopCodec);
        CODECS.put(Short.class, noopCodec);
        CODECS.put(Integer.class, noopCodec);
        CODECS.put(Long.class, noopCodec);
        CODECS.put(Date.class, noopCodec);
        CODECS.put(UUID.class, noopCodec);
        CODECS.put(Boolean.class, noopCodec);
        CODECS.put(ObjectId.class, noopCodec);
        CODECS.put(Character.class, new CharacterCodec());
    }

    public static IBsonCodec ofCodec(Class<?> type) {
        // TODO
        return CODECS.get(type);
    }

    public static class NoopCodec implements IBsonCodec {

        @Override
        public Object encode(Object to) {
            return to;
        }

        @Override
        public Object decode(Object from) {
            return from;
        }
    }
}
