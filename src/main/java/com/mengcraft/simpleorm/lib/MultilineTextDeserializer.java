package com.mengcraft.simpleorm.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Type;
import java.util.Collection;

public class MultilineTextDeserializer implements JsonDeserializer<Collection<?>> {

    @Override
    public Collection<?> deserialize(JsonElement element, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (element.isJsonPrimitive() && ((JsonPrimitive) element).isString()) {
            JsonArray array = new JsonArray();
            String[] split = element.getAsString().split("\n");
            for (String line : split) {
                array.add(new JsonPrimitive(line));
            }
            context.deserialize(array, type);
        }
        return null;
    }
}
