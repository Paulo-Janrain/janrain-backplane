package com.janrain.backplane.server;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Johnny Bufu
 */
public class MessageFrame extends AbstractMessage {

    // - PUBLIC

    @Override
    public String getIdValue() {
        return get(Field.MESSAGE_ID);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public static enum Field implements MessageField {

        // - PUBLIC
        // todo: update spec v2 to use these frame field names
        MESSAGE_ID("message_id"),
        CHANNEL_ID("channel_id"),
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

    /**
     * @return a time-based, lexicographically comparable message ID.
     */
    public static String generateMessageId() {
        return ISO8601.format(new Date()) + "-" + randomString(10);
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(MessageFrame.class);

    private static final Random random = new Random();

    private static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") {{
        setTimeZone(TimeZone.getTimeZone("GMT"));
    }};

    private static String randomString(int length) {
        byte[] randomBytes = new byte[length];
        random.nextBytes(randomBytes);
        for (int i = 0; i < length; i++) {
            byte b = randomBytes[i];
            int c = Math.abs(b % 36);
            if (c < 26) c += 97; // map (0..25) to 'a' .. 'z'
            else c += (48 - 26);   // map (26..35) to '0'..'9'
            randomBytes[i] = (byte) c;
        }
        try {
            return new String(randomBytes, "US-ASCII");
        }
        catch (UnsupportedEncodingException e) {
            logger.error("US-ASCII character encoding not supported", e); // shouldn't happen
            return null;
        }
    }
}
