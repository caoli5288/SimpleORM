package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Maps;

import java.util.Map;

public class EnumCodec implements ICodec {

    private final Map<String, Enum<?>> constants = Maps.newHashMap();

    public EnumCodec(Class<?> cls) {
        for (Enum<?> e : ((Class<Enum<?>>) cls).getEnumConstants()) {
            constants.put(e.name(), e);
        }
    }

    @Override
    public Object encode(Object to) {
        return to.toString();
    }

    @Override
    public Object decode(Object from) {
        return constants.get(from.toString());
    }
}
