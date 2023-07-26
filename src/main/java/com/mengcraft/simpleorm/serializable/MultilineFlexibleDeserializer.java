package com.mengcraft.simpleorm.serializable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Type;

public class MultilineFlexibleDeserializer implements JsonDeserializer<Iterable<?>> {

    @Override
    public Iterable<?> deserialize(JsonElement element, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (element.isJsonArray()) {
            return ((DelegatedTypeAdapter<Iterable>.GsonContextImpl) context).delegated(element);
        } else if (element.isJsonObject()) {
            return wrap(element, type, context);
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive it = (JsonPrimitive) element;
            if (it.isString()) {
                String line = it.getAsString();
                JsonArray list = new JsonArray();
                for (String seg : line.split("\n")) {
                    list.add(seg);
                }
                return context.deserialize(list, type);
            } else {
                return wrap(element, type, context);
            }
        }
        throw new JsonParseException("Cannot deserialize " + element);
    }

    private static Iterable<?> wrap(JsonElement element, Type type, JsonDeserializationContext context) {
        JsonArray list = new JsonArray();
        list.add(element);
        return context.deserialize(list, type);
    }
}
