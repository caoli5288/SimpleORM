package com.mengcraft.simpleorm.lib;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode
@ToString
public class Tuple<L, R> {

    private final L left;
    private final R right;

    public Tuple(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public static <L, R> Tuple<L, R> tuple(L left, R right) {
        return new Tuple<>(left, right);
    }

    public L left() {
        return left;
    }

    public R right() {
        return right;
    }

    public static <L, R> Tuple<List<L>, List<R>> flip(List<Tuple<L, R>> input) {
        List<L> ll = new ArrayList<>(input.size());
        List<R> lr = new ArrayList<>(input.size());
        for (Tuple<L, R> tuple : input) {
            ll.add(tuple.left);
            lr.add(tuple.right);
        }
        return tuple(ll, lr);
    }
}
