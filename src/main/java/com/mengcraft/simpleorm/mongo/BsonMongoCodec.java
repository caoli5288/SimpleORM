package com.mengcraft.simpleorm.mongo;

import com.mongodb.DBObject;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.Map;

@RequiredArgsConstructor
public class BsonMongoCodec implements IMongoCodec {

    private static final BsonMongoCodec INSTANCE = new BsonMongoCodec();

    @Override
    public DBObject encode(Object obj) {
        if (obj instanceof ConfigurationSerializable) {
            return encodeMap(((ConfigurationSerializable) obj).serialize());
        }
        if (obj instanceof Map) {
            return encodeMap((Map<String, Object>) obj);
        }
        return null;
    }

    private DBObject encodeMap(Map<String, Object> serialize) {
        return null;
    }

    @Override
    public <T> T decode(Class<T> cls, DBObject obj) {
        return null;
    }

    public static BsonMongoCodec getInstance() {
        return INSTANCE;
    }
}
