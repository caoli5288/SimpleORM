package com.mengcraft.simpleorm.async;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ClusterOptions {

    private int redisDb;
}
