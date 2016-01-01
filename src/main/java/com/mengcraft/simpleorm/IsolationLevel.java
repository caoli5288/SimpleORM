package com.mengcraft.simpleorm;

/**
 * Created on 16-1-2.
 */
public enum IsolationLevel {

    NONE(0),
    READ_UNCOMMITTED(1),
    READ_COMMITTED(2),
    REPEATABLE_READ(4),
    SERIALIZABLE(8);

    private final int rawLevel;

    public int getRawLevel() {
        return rawLevel;
    }

    public String getName() {
        return name();
    }

    IsolationLevel(int rawLevel) {
        this.rawLevel = rawLevel;
    }

}
