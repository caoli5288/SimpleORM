package com.mengcraft.simpleorm.cluster;

import com.mengcraft.simpleorm.lib.Utils;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeployOptions {

    private String name;
    @Builder.Default
    private int deploy = 1;
    @Builder.Default
    private int deployPerNode = 1;
    @Builder.Default
    private long ttl = 7;
    @Builder.Default
    private long consensusTimeout = 1000;

    public boolean valid() {
        return !Utils.isNullOrEmpty(name) && deploy > 0 && ttl > 0 && deployPerNode > 0 && consensusTimeout > 200;
    }
}
