package com.mengcraft.simpleorm.mongo;

import com.google.common.base.Preconditions;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public class BsonMongoCodec implements IMongoCodec {

    private final CodecRegistry codecRegistry;

    BsonMongoCodec() {
        CodecRegistry defaultCodecRegistry = MongoClientSettings.getDefaultCodecRegistry();
        codecRegistry = CodecRegistries.fromRegistries(defaultCodecRegistry,
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));
    }

    @Override
    public DBObject encode(Object obj) {
        Codec<Object> codec = (Codec<Object>) codecRegistry.get(obj.getClass());
        BsonDocumentWriter buf = new BsonDocumentWriter(new BsonDocument());
        codec.encode(buf, obj, EncoderContext.builder().build());
        return new BasicDBObject(buf.getDocument());
    }

    @Override
    public <T> T decode(Class<T> cls, DBObject obj) {
        Preconditions.checkArgument(obj instanceof BasicDBObject, "obj must be BasicDBObject");
        BsonDocument bson = ((BasicDBObject) obj).toBsonDocument(BasicDBObject.class, codecRegistry);
        return codecRegistry.get(cls).decode(new BsonDocumentReader(bson), DecoderContext.builder().build());
    }
}
