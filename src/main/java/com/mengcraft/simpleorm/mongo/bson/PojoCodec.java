package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.mengcraft.simpleorm.lib.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.persistence.Transient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

@RequiredArgsConstructor
public class PojoCodec implements ICodec {

    private final Constructor<?> constructor;
    private final List<Property> properties = Lists.newArrayList();

    @SneakyThrows
    public PojoCodec(Class<?> cls) {
        // TODO use bean factory
        constructor = cls.getConstructor();
        setup(cls);
    }

    private void setup(Class<?> cls) {
        Class<?> superCls = cls.getSuperclass();
        if (superCls != null && superCls != Object.class) {
            setup(superCls);
        }
        for (Field field : cls.getDeclaredFields()) {
            Property of = Property.of(field);
            if (of != null) {
                properties.add(of);
            }
        }
    }

    @Override
    public Object encode(Object to) {
        DBObject obj = new BasicDBObject();
        for (Property property : properties) {
            Object encoded = property.get(to);
            if (encoded != null) {
                obj.put(property.fieldName, encoded);
            }
        }
        return obj;
    }

    @Override
    @SneakyThrows
    public Object decode(Object from) {
        // TODO use bean factory
        DBObject obj = (DBObject) from;
        Object instance = constructor.newInstance();
        for (Property field : properties) {
            Object value = obj.get(field.fieldName);
            if (value != null) {
                field.set(instance, value);
            }
        }
        return instance;
    }

    @RequiredArgsConstructor
    private static class Property {

        private final String fieldName;
        private final Field field;
        private final ICodec ofCodec;

        public static Property of(Field field) {
            // check transients
            int modifiers = field.getModifiers();
            if ((modifiers & Modifier.TRANSIENT) != 0 || (modifiers & Modifier.STATIC) != 0) {
                return null;
            }
            Transient isTransient = field.getDeclaredAnnotation(Transient.class);
            if (isTransient != null) {
                return null;
            }
            // try set accessible first
            field.setAccessible(true);
            // codecs
            ICodec ofCodec = CodecMap.ofCodec(field.getType());
            String fieldName = field.getName();
            SerializedName serializedName = field.getDeclaredAnnotation(SerializedName.class);
            if (serializedName != null && Utils.isNullOrEmpty(serializedName.value())) {
                fieldName = serializedName.value();
            }
            return new Property(fieldName, field, ofCodec);
        }

        @SneakyThrows
        public Object get(Object to) {
            return ofCodec.encode(field.get(to));
        }

        @SneakyThrows
        public void set(Object obj, Object value) {
            field.set(obj, ofCodec.decode(value));
        }
    }
}
