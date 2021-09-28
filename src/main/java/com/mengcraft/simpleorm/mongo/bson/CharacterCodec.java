package com.mengcraft.simpleorm.mongo.bson;

public class CharacterCodec implements ICodec {

    @Override
    public Object encode(Object to) {
        return to.toString();
    }

    @Override
    public Object decode(Object from) {
        return from.toString().charAt(0);
    }
}
