package com.mengcraft.simpleorm;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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

        public <T> List<T> findAll(Class<T> clz, DBObject ref) {
            DBCursor cursor = collection.find(ref);
            List<T> container = new ArrayList<>();
            for (DBObject object : cursor) {
                container.add(ORM.deserialize(clz, (Map<String, Object>) object.toMap()));
            }
            return container;
        }

        public <T> T find(Class<T> clz, DBObject find) {
            DBObject result = collection.findOne(find);
            if (nil(result)) {
                return null;
            }
            return ORM.deserialize(clz, (Map<String, Object>) result.toMap());
        }

        public <T> T find(Class<T> clz, Object id) {
            DBObject result = collection.findOne(id);
            if (nil(result)) {
                return null;
            }
            return ORM.deserialize(clz, (Map<String, Object>) result.toMap());
        }
    }
}
