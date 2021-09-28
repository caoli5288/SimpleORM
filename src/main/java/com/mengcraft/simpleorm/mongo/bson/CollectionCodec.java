package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;
import com.mongodb.BasicDBList;
import lombok.SneakyThrows;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

public class CollectionCodec implements ICodec {

    private static final Map<Class<?>, Class<?>> COLLECTIONS = Maps.newHashMap();

    static {
        COLLECTIONS.put(Collection.class, ArrayList.class);
        COLLECTIONS.put(List.class, ArrayList.class);
        COLLECTIONS.put(BlockingDeque.class, LinkedBlockingDeque.class);
        COLLECTIONS.put(BlockingQueue.class, LinkedBlockingQueue.class);
        COLLECTIONS.put(Deque.class, LinkedList.class);
        COLLECTIONS.put(NavigableSet.class, TreeSet.class);
        COLLECTIONS.put(Queue.class, LinkedList.class);
        COLLECTIONS.put(Set.class, HashSet.class);
        COLLECTIONS.put(SortedSet.class, TreeSet.class);
        COLLECTIONS.put(TransferQueue.class, LinkedTransferQueue.class);
    }

    private final Constructor<?> constructor;

    @SneakyThrows
    public CollectionCodec(Class<?> cls) {
        constructor = COLLECTIONS.getOrDefault(cls, cls).getConstructor();
    }

    @Override
    public Object encode(Object to) {
        Collection<?> collection = (Collection) to;
        BasicDBList obj = new BasicDBList();
        for (Object entry : collection) {
            obj.add(CodecMap.encode(entry));
        }
        return obj;
    }

    @Override
    @SneakyThrows
    public Object decode(Object from) {
        Collection<Object> collection = (Collection) constructor.newInstance();
        for (Object entry : ((Collection) from)) {
            collection.add(CodecMap.decode(entry));
        }
        return collection;
    }
}
