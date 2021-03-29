package com.mengcraft.simpleorm.async;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Message {

    @SerializedName("a")
    private String sender;
    @SerializedName("b")
    private String receiver;
    @SerializedName("c")
    private String contents;
    @SerializedName("d")
    private String contentsType;

    /**
     * unsigned int32
     */
    @SerializedName("e")
    private long id;
    @SerializedName("f")
    private long futureId;
}
