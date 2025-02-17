package com.mengcraft.simpleorm.mongo;

import com.mengcraft.simpleorm.ORM;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import java.util.Map;

public class LegacyMongoCodec implements IMongoCodec {

    @Override
    public DBObject encode(Object obj) {
        Map<String, Object> map = ORM.serialize(obj);
        return new BasicDBObject(map);
    }

    @Override
    public <T> T decode(Class<T> cls, DBObject obj) {
        return ORM.deserialize(cls, (BasicDBObject) obj);
    }
}
