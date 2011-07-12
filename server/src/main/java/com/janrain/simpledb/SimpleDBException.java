package com.janrain.simpledb;

/**
 * Wrapper for Amazon's runtime (Client)Exception.
 *
 * @author Johnny Bufu
 */
public class SimpleDBException extends Exception {

    public SimpleDBException(String message) {
        super(message);
    }

    public SimpleDBException(String message, Throwable cause) {
        super(message, cause);
    }

    public SimpleDBException(Throwable cause) {
        super(cause);
    }
}
