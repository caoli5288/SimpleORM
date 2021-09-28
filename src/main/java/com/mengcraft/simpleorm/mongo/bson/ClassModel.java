package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.mengcraft.simpleorm.lib.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class ClassModel {

    @Getter
    private final Class<?> cls;
    private final List<FieldModel> fields = Lists.newArrayList();

    public ClassModel(Class<?> cls) {
        this.cls = cls;
        setup(cls);
    }

    private void setup(Class<?> cls) {
        Class<?> superCls = cls.getSuperclass();
        if (superCls != null && superCls != Object.class) {
            setup(superCls);
        }
        for (Field field : cls.getDeclaredFields()) {
            FieldModel of = FieldModel.of(field);
            if (of != null) {
                fields.add(of);
            }
        }
    }

    public DBObject encode(Object to) {
        DBObject obj = new BasicDBObject();
        for (FieldModel field : fields) {
            Object encoded = field.get(to);
            if (encoded != null) {
                obj.put(field.fieldName, encoded);
            }
        }
        return obj;
    }

    @SneakyThrows
    public Object decode(DBObject from) {
        // TODO use bean factory
        Object obj = cls.newInstance();
        for (FieldModel field : fields) {
            Object value = from.get(field.fieldName);
            if (value != null) {
                field.set(obj, value);
            }
        }
        return obj;
    }

    @RequiredArgsConstructor
    private static class FieldModel {

        private final String fieldName;
        private final Field field;
        private final ICodec ofCodec;

        public static FieldModel of(Field field) {
            // check transients
            int modifiers = field.getModifiers();
            if ((modifiers & Modifier.TRANSIENT) != 0 || (modifiers & Modifier.STATIC) != 0) {
                return null;
            }
            Transient isTransient = field.getDeclaredAnnotation(Transient.class);
            if (isTransient != null) {
                return null;
            }
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            // codecs
            ICodec ofCodec = CodecMap.ofCodec(field.getType());
            String fieldName = field.getName();
            SerializedName serializedName = field.getDeclaredAnnotation(SerializedName.class);
            if (serializedName != null && Utils.isNullOrEmpty(serializedName.value())) {
                fieldName = serializedName.value();
            }
            return new FieldModel(fieldName, field, ofCodec);
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
