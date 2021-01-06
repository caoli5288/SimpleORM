package com.mengcraft.simpleorm.serializable;

import java.util.Map;

public interface IDeserializer<T> {

    T deserialize(Class<T> cls, Map<String, ?> map);
}
