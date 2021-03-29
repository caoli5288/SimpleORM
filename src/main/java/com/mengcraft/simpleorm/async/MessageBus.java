package com.mengcraft.simpleorm.async;

import com.mengcraft.simpleorm.ORM;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class MessageBus implements Consumer<byte[]> {

    private final ClusterSystem system;

    MessageBus(ClusterSystem system) {
        this.system = system;
    }

    @Override
    public void accept(byte[] contents) {
        system.executor.execute(() -> {
            Message msg = ORM.json().fromJson(new String(contents, StandardCharsets.UTF_8), Message.class);
            system.receive(msg);
        });
    }
}
