package com.mengcraft.simpleorm.mongo.bson;

import java.sql.Timestamp;
import java.util.Date;

public class TimestampCodec implements ICodec {

    @Override
    public Object encode(Object to) {// encode as is
        return to;
    }

    @Override
    public Object decode(Object from) {
        Date date = (Date) from;
        return new Timestamp(date.getTime());
    }
}
