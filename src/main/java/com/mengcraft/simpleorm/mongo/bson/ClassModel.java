package com.mengcraft.simpleorm.mongo.bson;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.mengcraft.simpleorm.lib.Utils;
import lombok.RequiredArgsConstructor;

import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class ClassModel {

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

    @RequiredArgsConstructor
    private static class FieldModel {

        private final String fieldName;
        private final Field field;
        private final IBsonCodec ofCodec;

        public static FieldModel of(Field field) {
            // check transients
            int modifiers = field.getModifiers();
            if ((modifiers & Modifier.TRANSIENT) != 0) {
                return null;
            }
            Transient isTransient = field.getDeclaredAnnotation(Transient.class);
            if (isTransient != null) {
                return null;
            }
            // codecs
            IBsonCodec ofCodec = BsonCodecs.ofCodec(field.getType());
            String fieldName = field.getName();
            SerializedName serializedName = field.getDeclaredAnnotation(SerializedName.class);
            if (serializedName != null && Utils.isNullOrEmpty(serializedName.value())) {
                fieldName = serializedName.value();
            }
            return new FieldModel(fieldName, field, ofCodec);
        }
    }
}
