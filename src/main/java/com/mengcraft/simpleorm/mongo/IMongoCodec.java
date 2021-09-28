package com.mengcraft.simpleorm.mongo;

import com.mongodb.DBObject;

public interface IMongoCodec {

    DBObject encode(Object obj);

    <T> T decode(Class<T> cls, DBObject obj);
}
