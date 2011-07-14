/*
 * Copyright 2011 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.server;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;
import org.apache.commons.lang.StringUtils;
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
        String sticky = extractFieldValueAsJsonString(Field.STICKY, data);
        if (StringUtils.isNotEmpty(sticky)) {
            d.put(Field.STICKY.getFieldName(), sticky);
        }
        d.put(Field.PAYLOAD.getFieldName(), extractFieldValueAsJsonString(Field.PAYLOAD, data));
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
        STICKY("sticky", false),
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

        // todo: more validation

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

    private String extractFieldValueAsJsonString(Field field, Map<String,String> data) throws BackplaneServerException {
        try {
            return (new ObjectMapper()).writeValueAsString(data.get(field.getFieldName()));
        } catch (IOException e) {
            String errMsg = "Error serializing message payload: " + e.getMessage();
            logger.error(errMsg);
            throw new BackplaneServerException(errMsg, e);
        }
    }
}
