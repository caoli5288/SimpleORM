package com.mengcraft.simpleorm.mongo.bson;

public class SimpleCodec implements ICodec {

    private static final SimpleCodec INSTANCE = new SimpleCodec();

    @Override
    public Object encode(Object to) {
        return CodecMap.encode(to);
    }

    @Override
    public Object decode(Object from) {
        return CodecMap.decode(from);
    }

    public static SimpleCodec getInstance() {
        return INSTANCE;
    }
}
