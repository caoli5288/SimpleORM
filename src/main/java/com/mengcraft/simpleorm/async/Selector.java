package com.mengcraft.simpleorm.async;

import com.google.common.collect.Lists;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class Selector {

    Ops ops;
    int count;
    String category;
    List<String> results = Lists.newArrayList();

    public enum Ops {

        ONE,
        MANY,
        ALL
    }
}
