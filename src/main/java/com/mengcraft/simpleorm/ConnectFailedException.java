package com.mengcraft.simpleorm;

/**
 * Created on 16-1-2.
 */
public class ConnectFailedException extends RuntimeException {

    public ConnectFailedException() {
        super();
    }

    public ConnectFailedException(String message) {
        super(message);
    }

    public ConnectFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectFailedException(Throwable cause) {
        super(cause);
    }
}
