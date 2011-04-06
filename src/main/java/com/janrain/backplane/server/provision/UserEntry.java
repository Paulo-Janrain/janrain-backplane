package com.janrain.backplane.server.provision;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Johnny Bufu
 */
public class UserEntry extends AbstractMessage {

    // - PUBLIC

    @Override
    public String getIdValue() {
        return get(Field.USER);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        // - PUBLIC

        USER,
        PWDHASH;

        @Override
        public String getFieldName() {
            return name();
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void validate(String value) throws RuntimeException {
            if (isRequired()) validateNotNull(name(), value);
        }
    }
}
