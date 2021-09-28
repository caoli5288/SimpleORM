package com.mengcraft.simpleorm.mongo.bson;

public class NoopCodec implements ICodec {

    public static final NoopCodec INSTANCE = new NoopCodec();

    @Override
    public Object encode(Object to) {
        return to;
    }

    @Override
    public Object decode(Object from) {
        return from;
    }

    public static NoopCodec getInstance() {
        return INSTANCE;
    }
}
