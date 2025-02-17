package com.mengcraft.simpleorm.mongo;

public class MongoCodecs {

    public static final IMongoCodec LEGACY = new LegacyMongoCodec();
    public static final IMongoCodec BSON = new BsonMongoCodec();
}
