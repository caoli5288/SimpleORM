package com.mengcraft.simpleorm.mongo.bson;

import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor
public class NumberCodec implements ICodec {

    private final Function<Number, Number> convert;

    @Override
    public Object encode(Object to) {// encode as is
        return to;
    }

    @Override
    public Object decode(Object from) {
        return convert.apply((Number) from);
    }
}
