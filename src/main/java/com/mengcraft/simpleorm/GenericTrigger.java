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

    /**
     * @deprecated
     */
    public TriggerListener on(@NonNull String category, @NonNull BiConsumer<ImmutableMap<String, Object>, Map<String, Object>> consumer) {
        return on(category, processor(consumer));
    }

    public TriggerListener on(@NonNull String category, @NonNull IProcessor processor) {
        TriggerListener listener = new TriggerListener(category, processor);
        functions.put(category, listener);
        return listener;
    }

    public Map<String, Object> trigger(@NonNull String category, @NonNull ImmutableMap<String, Object> params) {
        Map<String, Object> object = new HashMap<>();
        for (TriggerListener listener : functions.get(category)) {
            listener.processor.process(params, object);
        }
        return object;
    }

    public Map<String, Object> trigger(@NonNull String category, @NonNull ConfigurationSerializable serializable) {
        return trigger(category, ImmutableMap.copyOf(serializable.serialize()));
    }

    public Map<String, Object> trigger(@NonNull String category) {
        return trigger(category, ImmutableMap.of());
    }

    private IProcessor processor(BiConsumer<ImmutableMap<String, Object>, Map<String, Object>> consumer) {
        return consumer::accept;
    }

    public interface IProcessor {

        void process(ImmutableMap<String, Object> params, Map<String, Object> result);
    }

    @EqualsAndHashCode(of = "id")
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class TriggerListener {

        private final UUID id = UUID.randomUUID();
        private final String category;
        private final IProcessor processor;

        public boolean cancel() {
            return functions.remove(category, this);
        }
    }

}
