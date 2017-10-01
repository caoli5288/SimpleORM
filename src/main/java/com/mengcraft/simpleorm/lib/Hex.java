package com.mengcraft.simpleorm.lib;

/**
 * Created by on 2017/9/29.
 */
public class Hex {

    public static final char[] HEX = {'0', '1', '2', '3', '4',
            '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'
    };

    private Hex() {
        // utility class
    }

    public static String hex(byte[] input) {
        if (input == null) throw new NullPointerException();
        StringBuilder buf = new StringBuilder();
        for (byte b : input) {
            buf.append(HEX[b >>> 4 & 0xf]);
            buf.append(HEX[b & 0xf]);
        }
        return buf.toString();
    }

}
