package com.mengcraft.simpleorm.async;

import java.util.function.Consumer;

public class MessageBus implements Consumer<byte[]> {

    private final ClusterSystem system;

    MessageBus(ClusterSystem system) {
        this.system = system;
    }

    @Override
    public void accept(byte[] contents) {
        system.receive(contents);
    }
}
