package com.mengcraft.simpleorm.lib;

import lombok.SneakyThrows;
import lombok.val;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import static com.mengcraft.simpleorm.lib.Hex.hex;

public final class MD5 {

    static final ThreadLocal<MessageDigest> INST = ThreadLocal.withInitial(MD5::load);

    @SneakyThrows
    private static MessageDigest load() {
        return MessageDigest.getInstance("MD5");
    }

    public static String digest(String in) {
        if (in == null) {
            throw new NullPointerException();
        }
        return digest(in.getBytes());
    }

    @SneakyThrows
    public static String digest(byte[] in) {
        if (in == null) {
            throw new NullPointerException();
        }
        val md = INST.get();
        md.update(in);
        byte[] out = md.digest();
        return hex(out);
    }

    public static void update(byte[] input) {
        if (input == null) {
            throw new NullPointerException();
        }
        INST.get().update(input);
    }

    public static void update(ByteBuffer input) {
        if (input == null) {
            throw new NullPointerException();
        }
        INST.get().update(input);
    }

    public static String digest() {
        return hex(INST.get().digest());
    }

    public static void reset() {
        INST.get().reset();
    }

}
