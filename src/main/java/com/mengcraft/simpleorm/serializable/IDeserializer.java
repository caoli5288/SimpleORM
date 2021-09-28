package com.mengcraft.simpleorm.serializable;

import java.util.Map;

public interface IDeserializer {

    Object deserialize(Class<?> cls, Map<String, Object> map);
}
