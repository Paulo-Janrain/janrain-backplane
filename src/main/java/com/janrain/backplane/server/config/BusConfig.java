package com.janrain.backplane.server.config;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Johnny Bufu
 */
public class BusConfig extends AbstractMessage {

    // - PUBLIC

    @Override
    public String getIdValue() {
        return get(Field.BUS_ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        BUS_ID,

        RETENTION_TIME_SECONDS {
            @Override
            public void validate(String value) throws RuntimeException {
                if (isRequired() || value != null) {
                    validateInt(getFieldName(), value);
                }
            }};

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
