package com.mengcraft.simpleorm.redis;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.AbstractList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated still in develop
 */
@EqualsAndHashCode(exclude = "bucket", callSuper = false)
@Data
@ToString(exclude = "bucket")
public class RedisLiveList extends AbstractList<String> implements Deque<String> {

    private final RedisLiveObjectBucket bucket;
    private final String id;

    RedisLiveList(RedisLiveObjectBucket bucket, String id) {
        this.bucket = bucket;
        this.id = bucket.getId() + ":" + id;
    }

    @AllArgsConstructor
    private static class LazyCursor implements ListIterator<String> {

        private final RedisLiveList parent;
        private int cursor;
        private boolean descending;
        private String next;

        @Override
        public boolean hasNext() {
            next = parent.get(nextIndex());
            return next != null;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            cursor = nextIndex();
            String element = next;
            next = null;
            return element;
        }

        @Override
        public boolean hasPrevious() {
            return cursor != 0;
        }

        @Override
        public String previous() {
            if (!hasPrevious()) {
                throw new NoSuchElementException();
            }
            cursor = previousIndex();
            return parent.get(cursor);
        }

        @Override
        public int nextIndex() {
            return cursor + (descending ? -1 : 1);
        }

        @Override
        public int previousIndex() {
            return cursor - (descending ? -1 : 1);
        }

        @Override
        public void remove() {
            parent.remove(cursor);
            cursor -= descending ? -1 : 1;
        }

        @Override
        public void set(String element) {
            parent.set(cursor, element);
        }

        @Override
        public void add(String s) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Iterator<String> descendingIterator() {
        return new LazyCursor(this, 0, true, null);
    }

    @Override
    public Iterator<String> iterator() {
        return listIterator();
    }

    @Override
    public ListIterator<String> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<String> listIterator(int index) {
        return new LazyCursor(this, index, false, null);
    }

    @Override
    public boolean remove(Object obj) {
        return removeFirstOccurrence(obj);
    }

    @Override
    public int size() {
        return (int) length();
    }

    public long length() {
        return bucket.getRedisWrapper().call(jedis -> jedis.llen(id));
    }

    /**
     * @return always null
     */
    @Override
    public String set(int index, String element) {
        bucket.getRedisWrapper().open(jedis -> jedis.lset(id, index, element));
        return null;
    }

    @Override
    public void add(int index, String element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addFirst(String value) {
        push(value);
    }

    @Override
    public void addLast(String value) {
        add(value);
    }

    @Override
    public boolean add(String value) {
        return offer(value);
    }

    @Override
    public boolean offerFirst(String value) {
        push(value);
        return true;
    }

    @Override
    public boolean offerLast(String value) {
        addLast(value);
        return true;
    }

    @Override
    public String removeFirst() {
        return remove();
    }

    @Override
    public String removeLast() {
        String last = pollLast();
        if (last == null) {
            throw new NoSuchElementException(String.format("Exception occurred while do remove last in %s", this));
        }
        return last;
    }

    @Override
    public String pollFirst() {
        return bucket.getRedisWrapper().call(jedis -> jedis.lpop(id));
    }

    @Override
    public String pollLast() {
        return bucket.getRedisWrapper().call(jedis -> jedis.rpop(id));
    }

    /**
     * @param timeout the maximum number of seconds to block
     * @see java.util.concurrent.BlockingDeque#pollFirst(long, TimeUnit)
     */
    public String pollFirst(int timeout) {
        List<String> result = bucket.getRedisWrapper().call(jedis -> jedis.blpop(timeout, id));
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    /**
     * @param timeout the maximum number of seconds to block
     * @see java.util.concurrent.BlockingDeque#pollLast(long, TimeUnit)
     */
    public String pollLast(int timeout) {
        List<String> result = bucket.getRedisWrapper().call(jedis -> jedis.brpop(timeout, id));
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    @Override
    public String getFirst() {
        String first = peekFirst();
        if (first == null) {
            throw new NoSuchElementException(String.format("Exception occurred while do get first in %s", this));
        }
        return first;
    }

    @Override
    public String getLast() {
        String last = peekLast();
        if (last == null) {
            throw new NoSuchElementException(String.format("Exception occurred while do get last in %s", this));
        }
        return null;
    }

    @Override
    public String peekFirst() {
        return get(0);
    }

    @Override
    public String peekLast() {
        return get(-1);
    }

    @Override
    public boolean removeFirstOccurrence(Object value) {
        if (!(value instanceof CharSequence)) {
            throw new ClassCastException(String.format("Value %s not instance of char sequence", value));
        }
        return remove(String.valueOf(value), 1) == 1;
    }

    @Override
    public boolean removeLastOccurrence(Object value) {
        if (!(value instanceof CharSequence)) {
            throw new ClassCastException(String.format("Value %s not instance of char sequence", value));
        }
        return remove(String.valueOf(value), -1) == 1;
    }

    /**
     * Removes the first count occurrences of elements equal to value from the list stored at key. The count argument influences the operation in the following ways:
     *
     * <li>count > 0: Remove elements equal to value moving from head to tail.</li>
     * <li>count < 0: Remove elements equal to value moving from tail to head.</li>
     * <li>count = 0: Remove all elements equal to value.</li>
     * <p></p>
     * For example, LREM list -2 "hello" will remove the last two occurrences of "hello" in the list stored at list.
     * <p></p>
     * Note that non-existing keys are treated like empty lists, so when key does not exist, the command will always return 0.
     *
     * @return the number of removed elements
     */
    public long remove(String value, int count) {
        return bucket.getRedisWrapper().call(jedis -> jedis.lrem(id, count, value));
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
        return removeFirst();
    }

    /**
     * Returns the element at index in the list. The index is zero-based, so 0 means the first element, 1 the second element and so on.
     * Negative indices can be used to designate elements starting at the tail of the list.
     * Here, -1 means the last element, -2 means the penultimate and so forth.
     * <p></p>
     * When the value at key is not a list, an error is returned.
     *
     * @return the requested element, or null when index is out of range
     */
    @Override
    public String get(int index) {
        return bucket.getRedisWrapper().call(jedis -> jedis.lindex(id, index));
    }

    @Override
    public boolean offer(String s) {
        append(array(s));
        return true;
    }

    @Override
    public String remove() {
        String first = pollFirst();
        if (first == null) {
            throw new NoSuchElementException();
        }
        return first;
    }

    /**
     * Insert all the specified values at the tail of the list.
     *
     * @return the length of the list after the operation
     */
    public long append(String[] values) {
        return bucket.getRedisWrapper().call(jedis -> jedis.rpush(id, values));
    }

    @Override
    public String poll() {
        return pollFirst();
    }

    @Override
    public String element() {
        return getFirst();
    }

    @Override
    public String peek() {
        return peekFirst();
    }

    /**
     * @return immutable view of this live list
     */
    public List<String> subList(long start, long stop) {
        return bucket.getRedisWrapper().call(jedis -> jedis.lrange(id, start, stop));
    }

    /**
     * Trim an existing list so that it will contain only the specified range of elements specified.
     * Both start and stop are zero-based indexes, where 0 is the first element of the list (the head), 1 the next element and so on.
     */
    public void trim(long start, long stop) {
        bucket.getRedisWrapper().open(jedis -> jedis.ltrim(id, start, stop));
    }

    private static String indexOfScript;

    @Override
    public int indexOf(Object obj) {
        if (!(obj instanceof CharSequence)) {
            throw new ClassCastException();
        }
        if (indexOfScript == null) {
            indexOfScript = bucket.getRedisWrapper().call(jedis -> jedis.scriptLoad("local index = 0\n" +
                    "while 1 do\n" +
                    "  local value = redis.call('LINDEX', KEYS[0], index)\n" +
                    "  if value == nil then\n" +
                    "    return -1\n" +
                    "  end\n" +
                    "  if value == ARGV[0] then\n" +
                    "    return index\n" +
                    "  end\n" +
                    "  index = index + 1\n" +
                    "end"));
        }
        return ((Number) bucket.getRedisWrapper().call(jedis -> jedis.evalsha(indexOfScript, ImmutableList.of(id), ImmutableList.of(obj.toString())))).intValue();
    }

    @Override
    public boolean contains(Object obj) {
        return indexOf(obj) != -1;
    }

    private static String[] array(String... elements) {
        return elements;
    }
}
