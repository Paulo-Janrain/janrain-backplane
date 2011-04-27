package com.janrain.message;

/**
 * A simple field definition that may be part of a (name/value) message.
 */
public interface MessageField {

    String getFieldName();

    boolean isRequired();

    void validate(String value) throws RuntimeException;

}
