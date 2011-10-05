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

package com.janrain.backplane.server.metrics;

import com.janrain.backplane.server.BackplaneServerException;
import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;
import org.codehaus.jackson.map.ObjectMapper;

import javax.ws.rs.GET;
import java.io.IOException;
import java.util.*;

/**
 * @author Tom Raney
 */

public class MetricMessage extends AbstractMessage {

    public MetricMessage() {
    }

    public MetricMessage(String id, String time, String payload) throws BackplaneServerException {
        Map<String,String> d = new LinkedHashMap<String, String>();
        d.put(Field.ID.getFieldName(), id);
        d.put(Field.TIME.getFieldName(), time);
        d.put(Field.PAYLOAD.getFieldName(), payload);
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

    public static enum Field implements MessageField {

        ID("id"),
        TIME("time"),
        PAYLOAD("payload");

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
        private boolean required = true;

        private Field(String fieldName) {
            this(fieldName, true);
        }

        private Field(String fieldName, boolean required) {
            this.fieldName = fieldName;
            this.required = required;
        }

    }

    private String extractFieldValueAsJsonString(Field field, Map<String,Object> data) throws BackplaneServerException {
        try {
            Object value = data.get(field.getFieldName());
            return value == null ? null : (new ObjectMapper()).writeValueAsString(value);
        } catch (IOException e) {
            String errMsg = "Error serializing message payload: " + e.getMessage();
            throw new BackplaneServerException(errMsg, e);
        }
    }

}
