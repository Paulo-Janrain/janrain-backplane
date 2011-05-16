package com.janrain.backplane.server;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * @author Johnny Bufu
 */
public class BackplaneMessage extends AbstractMessage {

    // - PUBLIC

    public BackplaneMessage(String id, String bus, String channel, Map<String, String> data) throws BackplaneServerException {
        Map<String,String> d = new LinkedHashMap<String, String>(data);
        d.put(Field.ID.getFieldName(), id);
        d.put(Field.BUS.getFieldName(), bus);
        d.put(Field.CHANNEL_NAME.getFieldName(), channel);
        try {
            d.put(Field.PAYLOAD.getFieldName(), (new ObjectMapper()).writeValueAsString(data.get(Field.PAYLOAD.getFieldName())));
        } catch (IOException e) {
            String errMsg = "Error serializing message payload: " + e.getMessage();
            logger.error(errMsg);
            throw new BackplaneServerException(errMsg, e);
        }
        super.init(id, d);
    }

    @Override
    public String getIdValue() {
        return get(Field.ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public HashMap<String, Object> asFrame() throws BackplaneServerException {

        HashMap<String, Object> frame = new LinkedHashMap<String, Object>();

        frame.put(Field.ID.getFieldName(), get(BackplaneMessage.Field.ID.getFieldName()));
        frame.put(Field.CHANNEL_NAME.getFieldName(), get(BackplaneMessage.Field.CHANNEL_NAME.getFieldName()));

        Map <String,Object> msg = new LinkedHashMap<String, Object>(this);
        msg.remove(Field.ID.getFieldName());
        msg.remove(Field.BUS.getFieldName());
        msg.remove(Field.CHANNEL_NAME.getFieldName());
        try {
            msg.put(
                BackplaneMessage.Field.PAYLOAD.getFieldName(),
                (new ObjectMapper()).readValue(get(BackplaneMessage.Field.PAYLOAD), LinkedHashMap.class) );
        } catch (IOException e) {
            String errMsg = "Error deserializing message payload: " + e.getMessage();
            logger.error(errMsg);
            throw new BackplaneServerException(errMsg, e);
        }
        frame.put("message", msg);

        return frame;
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

    // - PACKAGE

    public BackplaneMessage() {
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneMessage.class);

}
