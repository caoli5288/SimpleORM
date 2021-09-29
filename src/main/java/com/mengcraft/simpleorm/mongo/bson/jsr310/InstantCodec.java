package com.mengcraft.simpleorm.mongo.bson.jsr310;

import com.mengcraft.simpleorm.mongo.bson.ICodec;

import java.time.Instant;

public class InstantCodec implements ICodec {

    @Override
    public Object encode(Object to) {// to int64
        Instant instant = (Instant) to;
        return instant.toEpochMilli();
    }

    @Override
    public Object decode(Object from) {
        long epoch = (long) from;
        return Instant.ofEpochMilli(epoch);
    }
}
