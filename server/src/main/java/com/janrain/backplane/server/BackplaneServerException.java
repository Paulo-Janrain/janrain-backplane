package com.janrain.backplane.server;

/**
 * @author Johnny Bufu
 */
public class BackplaneServerException extends Exception {

    public BackplaneServerException(String message) {
        super(message);
    }

    public BackplaneServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public BackplaneServerException(Throwable cause) {
        super(cause);
    }
}
