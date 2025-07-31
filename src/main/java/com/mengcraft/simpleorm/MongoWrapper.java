package com.mengcraft.simpleorm;

import com.mengcraft.simpleorm.mongo.IMongoCodec;
import com.mengcraft.simpleorm.mongo.MongoCodecs;
import com.mengcraft.simpleorm.mongo.MongoResourceManager;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoTimeoutException;
import com.mongodb.gridfs.GridFS;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.mengcraft.simpleorm.ORM.nil;

public class MongoWrapper implements Closeable {

    private final MongoClient client;

    @SneakyThrows
    MongoWrapper(MongoClientURI url) {
        client = new MongoClient(url);
    }

    public static MongoWrapper create(String url, ConfigurationSection mongo) {
        MongoClientOptions.Builder options = MongoClientOptions.builder()
                .connectionsPerHost(mongo.getInt("connections-per-host", 10))
                .connectTimeout(mongo.getInt("connect-timeout", 10000));
        return new MongoWrapper(new MongoClientURI(url, options));
    }

    @Override
    public void close() {
        client.close();
    }

    public static MongoWrapper b(String url) {
        return new MongoWrapper(new MongoClientURI(url));
    }

    /**
     * Create an MongoResourceManager for given plugin.
     *
     * @param plugin the owner plugin
     * @return then new instance
     */
    public IResourceManager openResourceManager(JavaPlugin plugin) {
        return new MongoResourceManager(plugin, openFileSystem("files", plugin.getName()));
    }

    public GridFS openFileSystem(String database, String bucket) {
        return new GridFS(client.getDB(database), bucket == null || bucket.isEmpty() ? "fs" : bucket);
    }

    public void ping() throws MongoTimeoutException {
        open("_ping", "_ping").collection.findOne();
    }

    public MongoDatabaseWrapper open(String database, String collection) {
        return open(database, collection, MongoCodecs.LEGACY);
    }

    public MongoDatabaseWrapper open(String database, String collection, IMongoCodec serializer) {
        return new MongoDatabaseWrapper(client.getDB(database).getCollection(collection), serializer);
    }

    @RequiredArgsConstructor
    public static class MongoDatabaseWrapper {

        private final DBCollection collection;
        private final IMongoCodec clsCodec;

        public void open(Consumer<DBCollection> consumer) {
            consumer.accept(collection);
        }

        public <T> T call(Function<DBCollection, T> function) {
            return function.apply(collection);
        }

        public <T> void save(T bean) {
            collection.save(clsCodec.encode(bean));
        }

        public <T> void save(T bean, Function<T, Object> ids) {
            DBObject serializer = this.clsCodec.encode(bean);
            serializer.put("_id", ids.apply(bean));
            collection.save(serializer);
        }

        public <T> List<T> findAll(Class<T> clz, DBObject ref) {
            DBCursor cursor = collection.find(ref);
            List<T> container = new ArrayList<>();
            for (DBObject object : cursor) {
                container.add(clsCodec.decode(clz, object));
            }
            cursor.close();
            return container;
        }

        public <T> DBCursorWrapper<T> findLazy(Class<T> clz, DBObject ref) {
            DBCursor cursor = collection.find(ref);
            return new DBCursorWrapper<>(clz, cursor, clsCodec);
        }

        public <T> T find(Class<T> clz, DBObject find) {
            DBObject result = collection.findOne(find);
            if (nil(result)) {
                return null;
            }
            return clsCodec.decode(clz, result);
        }

        public <T> T find(Class<T> clz, Object id) {
            DBObject result = collection.findOne(id);
            if (nil(result)) {
                return null;
            }
            return clsCodec.decode(clz, result);
        }

        public void remove(Object id) {
            collection.remove(new BasicDBObject("_id", id));
        }
    }

    @RequiredArgsConstructor
    public static class DBCursorWrapper<T> implements Iterator<T> {

        private final Class<T> clz;
        private final DBCursor origin;
        private final IMongoCodec clsCodec;

        public boolean hasNext() {
            return origin.hasNext();
        }

        public T next() {
            return clsCodec.decode(clz, origin.next());
        }

        protected void finalize() {// safe to memory leak
            origin.close();
        }
    }
}
