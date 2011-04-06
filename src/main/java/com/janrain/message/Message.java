package com.janrain.message;

import java.util.Set;

/**
 * Models an simple name/value pairs message:
 * an association of message name (identifier) with the list of its MessageFields.
 *
 * @author Johnny Bufu
 */
public interface Message {

    String getIdValue();

    Set<? extends MessageField> getFields();
}
