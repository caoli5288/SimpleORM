package com.mengcraft.simpleorm.mongo;

import com.mengcraft.simpleorm.mongo.bson.CodecMap;
import com.mengcraft.simpleorm.mongo.bson.ICodec;
import com.mongodb.DBObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BsonMongoCodec implements IMongoCodec {

    private static final BsonMongoCodec INSTANCE = new BsonMongoCodec();

    @Override
    public DBObject encode(Object obj) {
        ICodec ofCodec = CodecMap.ofCodec(obj.getClass());
        return (DBObject) ofCodec.encode(obj);
    }

    @Override
    public <T> T decode(Class<T> cls, DBObject obj) {
        return null;
    }

    public static BsonMongoCodec getInstance() {
        return INSTANCE;
    }
}
