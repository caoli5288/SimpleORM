package com.mengcraft.simpleorm;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import lombok.SneakyThrows;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.mengcraft.simpleorm.ORM.nil;

public class MongoWrapper {

    private final MongoClient client;

    @SneakyThrows
    MongoWrapper(MongoClientURI url) {
        client = new MongoClient(url);
    }

    public static MongoWrapper b(String url) {
        return new MongoWrapper(new MongoClientURI(url));
    }

    public MongoDatabaseWrapper open(String database, String collection) {
        return new MongoDatabaseWrapper(client.getDB(database).getCollection(collection));
    }

    public static class MongoDatabaseWrapper {

        private final DBCollection collection;

        MongoDatabaseWrapper(DBCollection collection) {
            this.collection = collection;
        }

        public void open(Consumer<DBCollection> consumer) {
            consumer.accept(collection);
        }

        public <T> void save(T bean) {
            collection.save(new BasicDBObject(ORM.serialize(bean)));
        }

        public <T> void save(T bean, Function<T, Object> idprovider) {
            Map<String, Object> serializer = ORM.serialize(bean);
            serializer.put("_id", idprovider.apply(bean));
            collection.save(new BasicDBObject(serializer));
        }

        public <T> T findOne(BasicDBObject find, Class<T> clz) {
            DBObject result = collection.findOne(find);
            if (nil(result)) {
                return null;
            }
            return ORM.deserialize((Map<String, Object>) result.toMap(), clz);
        }

        public <T> T findOne(Object idx, Class<T> clz) {
            DBObject result = collection.findOne(idx);
            if (nil(result)) {
                return null;
            }
            return ORM.deserialize((Map<String, Object>) result.toMap(), clz);
        }
    }
}
