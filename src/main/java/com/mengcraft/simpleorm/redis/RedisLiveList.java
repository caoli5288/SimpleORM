package com.mengcraft.simpleorm.redis;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.AbstractQueue;
import java.util.Deque;
import java.util.Iterator;

@EqualsAndHashCode(exclude = "bucket", callSuper = false)
@Data
@ToString(exclude = "bucket")
public class RedisLiveList extends AbstractQueue<String> implements Deque<String> {

    private final RedisLiveObjectBucket bucket;
    private final String id;

    RedisLiveList(RedisLiveObjectBucket bucket, String id) {// TODO
        this.bucket = bucket;
        this.id = bucket.getId() + ":" + id;
    }

    @Override
    public Iterator<String> iterator() {
        return null;
    }

    @Override
    public int size() {
        return (int) length();
    }

    public long length() {
        return bucket.getRedisWrapper().call(jedis -> jedis.llen(id));
    }

    @Override
    public void addFirst(String value) {
        push(value);
    }

    @Override
    public void addLast(String s) {

    }

    @Override
    public boolean offerFirst(String value) {
        push(value);
        return true;
    }

    @Override
    public boolean offerLast(String s) {
        return false;
    }

    @Override
    public String removeFirst() {
        return bucket.getRedisWrapper().call(jedis -> jedis.lpop(id));
    }

    @Override
    public String removeLast() {
        return null;
    }

    @Override
    public String pollFirst() {
        return null;
    }

    @Override
    public String pollLast() {
        return null;
    }

    @Override
    public String getFirst() {
        return null;
    }

    @Override
    public String getLast() {
        return null;
    }

    @Override
    public String peekFirst() {
        return null;
    }

    @Override
    public String peekLast() {
        return null;
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return false;
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return false;
    }

    @Override
    public void push(String value) {
        push(array(value));
    }

    /**
     * Insert all the specified values at the head of the list.
     *
     * @return the length of the list after the push operations
     */
    public long push(String[] values) {
        return bucket.getRedisWrapper().call(jedis -> jedis.lpush(id, values));
    }

    @Override
    public String pop() {
        return null;
    }

    @Override
    public Iterator<String> descendingIterator() {
        return null;
    }

    @Override
    public boolean offer(String s) {
        return false;
    }

    @Override
    public String poll() {
        return null;
    }

    @Override
    public String peek() {
        return null;
    }

    private static String[] array(String... elements) {
        return elements;
    }
}
