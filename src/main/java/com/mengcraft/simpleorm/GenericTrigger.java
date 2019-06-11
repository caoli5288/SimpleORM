package com.mengcraft.simpleorm;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;

import java.util.UUID;
import java.util.function.BiConsumer;

public class GenericTrigger {

    private final Multimap<String, TriggerListener> functions = ArrayListMultimap.create();

    public TriggerListener on(@NonNull String category, @NonNull BiConsumer<JSONObject, JSONObject> function) {
        TriggerListener listener = new TriggerListener(category, function);
        functions.put(category, listener);
        return listener;
    }

    public JSONObject trigger(@NonNull String category, @NonNull JSONObject params) {
        JSONObject object = new JSONObject();
        for (TriggerListener listener : functions.get(category)) {
            listener.function.accept(params, object);
        }
        return object;
    }

    public JSONObject trigger(@NonNull String category) {
        return trigger(category, new JSONObject());
    }

    @EqualsAndHashCode(exclude = "function")
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class TriggerListener {

        private final UUID id = UUID.randomUUID();
        private final String category;
        private final BiConsumer<JSONObject, JSONObject> function;

        public void cancel() {
            functions.remove(category, this);
        }
    }

}
