package com.mengcraft.simpleorm.mongo.bson;

import com.mongodb.DBObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PojoCodec implements ICodec {

    private final ClassModel model;

    @Override
    public Object encode(Object to) {
        return model.encode(to);
    }

    @Override
    public Object decode(Object from) {
        return model.decode((DBObject) from);
    }
}
