package com.mengcraft.simpleorm.mongo.bson.jsr310;

import com.mengcraft.simpleorm.mongo.bson.ICodec;

import java.time.LocalDate;

public class LocalDateCodec implements ICodec {

    @Override
    public Object encode(Object to) {// translate to int64
        LocalDate date = (LocalDate) to;
        return date.toEpochDay();
    }

    @Override
    public Object decode(Object from) {
        long epoch = (long) from;
        return LocalDate.ofEpochDay(epoch);
    }
}
