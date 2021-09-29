package com.mengcraft.simpleorm.mongo.bson.jsr310;

import com.mengcraft.simpleorm.mongo.bson.ICodec;

import java.time.LocalTime;

public class LocalTimeCodec implements ICodec {

    @Override
    public Object encode(Object to) {// to int32
        LocalTime time = (LocalTime) to;
        return time.toSecondOfDay();
    }

    @Override
    public Object decode(Object from) {
        int seconds = (int) from;
        return LocalTime.ofSecondOfDay(seconds);
    }
}
