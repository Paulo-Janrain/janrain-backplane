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

package com.janrain.backplane.server.config;

import com.janrain.message.AbstractMessage;
import com.janrain.message.MessageField;
import org.apache.commons.lang.StringUtils;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Johnny Bufu
 */
public class BusConfig extends AbstractMessage {

    // - PUBLIC

    @Override
    public String getIdValue() {
        return get(Field.BUS_NAME);
    }

    @Override
    public Set<? extends MessageField> getFields() {
        return EnumSet.allOf(Field.class);
    }

    public EnumSet<BackplaneConfig.BUS_PERMISSION> getPermissions(String user) {
        if (isBusConfigField(user)) {
            throw new IllegalArgumentException("Invalid user name: " + user);
        }

        String perms = get(user);
        EnumSet<BackplaneConfig.BUS_PERMISSION> result = EnumSet.noneOf(BackplaneConfig.BUS_PERMISSION.class);
        if (StringUtils.isNotBlank(perms)) {
            for(String perm : perms.split(",")) {
                result.add(BackplaneConfig.BUS_PERMISSION.valueOf(perm));
            }
        }
        return result;
    }

    public static enum Field implements MessageField {

        BUS_NAME,

        RETENTION_TIME_SECONDS {
            @Override
            public void validate(String value) throws RuntimeException {
                if (isRequired() || value != null) {
                    validateInt(getFieldName(), value);
                }
            }},

        RETENTION_STICKY_TIME_SECONDS {
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

    // - PRIVATE

    private boolean isBusConfigField(String name) {
        try {
            Field.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
