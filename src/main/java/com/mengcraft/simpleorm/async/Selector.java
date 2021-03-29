package com.mengcraft.simpleorm.async;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Selector {

    Ops ops;
    int count;
    String category;

    public enum Ops {

        ONE,
        MANY,
        ALL
    }
}
