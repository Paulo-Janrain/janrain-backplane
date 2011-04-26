package com.janrain.backplane.server;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;
import java.util.*;

/**
 * @author Johnny Bufu
 */
public class BackplaneMessage extends AbstractMessage {

    // - PUBLIC

    @Override
    public String getIdValue() {
        return get(Field.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public void setId(String id) {
        put(Field.ID.getFieldName(), id);
    }

    public void setBus(String bus) {
        put(Field.BUS.getFieldName(), bus);
    }


    public void setChannelName(String channelName) {
        put(Field.CHANNEL_NAME.getFieldName(), channelName);
    }

    public static enum Field implements MessageField {
        ID("id", false),
        CHANNEL_NAME("channel_name", false),
        BUS("bus", false),
        SOURCE("source"),
        TYPE("type"),
        PAYLOAD("payload");

        @Override
        public String getFieldName() {
            return fieldName;
        }

        @Override
        public boolean isRequired() {
            return required;
        }

        @Override
        public void validate(String value) throws RuntimeException {
            if (isRequired()) validateNotNull(name(), value);
        }

        // - PRIVATE

        private String fieldName;
        private boolean required;

        private Field(String fieldName) {
            this(fieldName, true);
        }

        private Field(String fieldName, boolean required) {
            this.fieldName = fieldName;
            this.required = required;
        }
    }
}
