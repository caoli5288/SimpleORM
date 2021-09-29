package com.mengcraft.simpleorm.mongo.bson.jsr310;

import com.mengcraft.simpleorm.mongo.bson.ICodec;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class LocalDateTimeCodec implements ICodec {

    @Override
    public Object encode(Object to) {
        return Date.from(((LocalDateTime) to).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public Object decode(Object from) {
        return LocalDateTime.ofInstant(((Date) from).toInstant(), ZoneId.systemDefault());
    }
}
