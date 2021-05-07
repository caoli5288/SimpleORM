package com.mengcraft.simpleorm.async;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class StateWrapper {

    @SerializedName("m")
    String message;
}
