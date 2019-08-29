package com.mengcraft.simpleorm;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * @deprecated Still unstable
 */
public class GenericTrigger {

    private final Multimap<String, TriggerListener> functions = ArrayListMultimap.create();

    public TriggerListener on(@NonNull String category, @NonNull BiConsumer<ImmutableMap<String, Object>, Map<String, Object>> function) {
        TriggerListener listener = new TriggerListener(category, function);
        functions.put(category, listener);
        return listener;
    }

    public Map<String, Object> trigger(@NonNull String category, @NonNull ImmutableMap<String, Object> params) {
        Map<String, Object> object = new HashMap<>();
        for (TriggerListener listener : functions.get(category)) {
            listener.function.accept(params, object);
        }
        return object;
    }

    public Map<String, Object> trigger(@NonNull String category) {
        return trigger(category, ImmutableMap.of());
    }

    @EqualsAndHashCode(exclude = "function")
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class TriggerListener {

        private final UUID id = UUID.randomUUID();
        private final String category;
        private final BiConsumer<ImmutableMap<String, Object>, Map<String, Object>> function;

        public void cancel() {
            functions.remove(category, this);
        }
    }

}
