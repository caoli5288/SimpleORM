package com.mengcraft.simpleorm;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoTimeoutException;
import com.mongodb.gridfs.GridFS;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    public GridFS openFileSystem(String database, String bucket) {
        return new GridFS(client.getDB(database), bucket == null || bucket.isEmpty() ? "fs" : bucket);
    }

    public void ping() throws MongoTimeoutException {
        open("_ping", "_ping").collection.findOne();
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

        public <T> T call(Function<DBCollection, T> function) {
            return function.apply(collection);
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
            cursor.close();
            return container;
        }

        public <T> DBCursorWrapper<T> findLazy(Class<T> clz, DBObject ref) {
            DBCursor cursor = collection.find(ref);
            return new DBCursorWrapper<>(clz, cursor);
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

    public static class DBCursorWrapper<T> implements Iterator<T> {

        private final Class<T> clz;
        private final DBCursor origin;

        DBCursorWrapper(Class<T> clz, DBCursor origin) {
            this.clz = clz;
            this.origin = origin;
        }

        public boolean hasNext() {
            return origin.hasNext();
        }

        public T next() {
            return ORM.deserialize(clz, (Map<String, Object>) origin.next().toMap());
        }

        protected void finalize() {// safe to memory leak
            origin.close();
        }
    }
}
