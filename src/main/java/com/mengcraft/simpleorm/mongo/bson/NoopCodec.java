package com.mengcraft.simpleorm.mongo.bson;

public class NoopCodec implements ICodec {

    @Override
    public Object encode(Object to) {
        return to;
    }

    @Override
    public Object decode(Object from) {
        return from;
    }
}
