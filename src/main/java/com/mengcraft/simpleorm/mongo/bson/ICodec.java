package com.mengcraft.simpleorm.mongo.bson;

public interface ICodec {

    Object encode(Object to);

    Object decode(Object from);
}
