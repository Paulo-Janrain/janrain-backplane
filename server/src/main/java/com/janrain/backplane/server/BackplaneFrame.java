package com.janrain.backplane.server;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Johnny Bufu
 */
public class BackplaneFrame extends AbstractMessage {

    // - PUBLIC

    public BackplaneFrame(BackplaneMessage message, boolean includePayload) {
        put(Field.ID.getFieldName(), message.get(BackplaneMessage.Field.ID.getFieldName()));
        message.remove(BackplaneMessage.Field.ID.getFieldName());

        put(Field.CHANNEL_NAME.getFieldName(), message.get(BackplaneMessage.Field.CHANNEL_NAME.getFieldName()));
        message.remove(BackplaneMessage.Field.CHANNEL_NAME.getFieldName());

        message.remove(BackplaneMessage.Field.BUS.getFieldName());
        if (! includePayload) {
            message.remove(BackplaneMessage.Field.PAYLOAD.getFieldName());
        }

        put(Field.MESSAGE.getFieldName(), message.toString());
    }

    @Override
    public String getIdValue() {
        return get(Field.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        // - PUBLIC
        ID("id"),
        CHANNEL_NAME("channel_name"),
        MESSAGE("message");

        @Override
        public String getFieldName() {
            return fieldName;
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public void validate(String value) throws RuntimeException {
            if (isRequired()) validateNotNull(name(), value);
        }

        // - PRIVATE

        private String fieldName;

        private Field(String fieldName) {
            this.fieldName = fieldName;
        }
    }

}
