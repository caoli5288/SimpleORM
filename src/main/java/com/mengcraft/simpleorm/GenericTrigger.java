package com.mengcraft.simpleorm;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * @deprecated Still unstable
 */
public class GenericTrigger {

    private final Multimap<String, TriggerListener> functions = ArrayListMultimap.create();

    public TriggerListener on(@NonNull String category, @NonNull BiConsumer<ImmutableMap<String, Object>, Map<String, Object>> consumer) {
        TriggerListener listener = new TriggerListener(category, consumer);
        functions.put(category, listener);
        return listener;
    }

    public Map<String, Object> trigger(@NonNull String category, @NonNull String key, @NonNull Object value) {
        return trigger(category, ImmutableMap.of(key, value));
    }

    public Map<String, Object> trigger(@NonNull String category, @NonNull ImmutableMap<String, Object> params) {
        Map<String, Object> res = new HashMap<>();
        for (TriggerListener listener : functions.get(category)) {
            listener.processor.accept(params, res);
        }
        return res;
    }

    public Map<String, Object> trigger(@NonNull String category, @NonNull ConfigurationSerializable serializable) {
        return trigger(category, ImmutableMap.copyOf(serializable.serialize()));
    }

    public Map<String, Object> trigger(@NonNull String category) {
        return trigger(category, ImmutableMap.of());
    }

    @EqualsAndHashCode(of = "id")
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class TriggerListener {

        private final UUID id = UUID.randomUUID();
        private final String category;
        private final BiConsumer<ImmutableMap<String, Object>, Map<String, Object>> processor;

        public boolean cancel() {
            return functions.remove(category, this);
        }
    }

}
