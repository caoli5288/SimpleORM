package com.mengcraft.simpleorm.cluster;

import com.mengcraft.simpleorm.lib.Utils;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DeployOptions {

    private String name;
    @Builder.Default
    private int count = 1;
    @Builder.Default
    private int keepAlive = 7;
    @Builder.Default
    private int deadline = 17;

    public boolean valid() {
        return !Utils.isNullOrEmpty(name) && count > 0 && keepAlive > 0 && deadline > 0 && keepAlive < (deadline / 2);
    }
}
