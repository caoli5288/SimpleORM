package com.mengcraft.simpleorm.mongo.bson;

public interface IBsonCodec {

    Object encode(Object to);

    Object decode(Object from);
}
