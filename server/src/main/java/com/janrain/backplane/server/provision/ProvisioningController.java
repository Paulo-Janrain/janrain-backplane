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

package com.janrain.backplane.server.provision;

import com.janrain.backplane.server.config.AuthException;
import com.janrain.backplane.server.config.BackplaneConfig;
import com.janrain.backplane.server.config.BusConfig;
import com.janrain.backplane.server.config.User;
import com.janrain.crypto.HmacHashUtils;
import com.janrain.message.AbstractMessage;
import com.janrain.simpledb.SuperSimpleDB;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling the API calls for backplane customer configuration provisioning.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/provision/*")
@SuppressWarnings({"UnusedDeclaration"})
public class ProvisioningController {

    // - PUBLIC

    @RequestMapping(value = "/bus/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> busList(@RequestBody ListRequest listRequest) throws AuthException {
        bpConfig.checkAdminAuth(listRequest.getAdmin(), listRequest.getSecret());
        return doList(BusConfig.class, listRequest.getEntities());
    }

    @RequestMapping(value = "/user/list", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Map<String, String>> userList(@RequestBody ListRequest listRequest) throws AuthException {
        bpConfig.checkAdminAuth(listRequest.getAdmin(), listRequest.getSecret());
        return doList(User.class, listRequest.getEntities());
    }

    @RequestMapping(value = "/bus/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> busDelete(@RequestBody ListRequest deleteRequest) throws AuthException {
        bpConfig.checkAdminAuth(deleteRequest.getAdmin(), deleteRequest.getSecret());
        return doDelete(BusConfig.class, deleteRequest.getEntities());
    }

    @RequestMapping(value = "/user/delete", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> userDelete(@RequestBody ListRequest deleteRequest) throws AuthException {
        bpConfig.checkAdminAuth(deleteRequest.getAdmin(), deleteRequest.getSecret());
        return doDelete(User.class, deleteRequest.getEntities());
    }

    @RequestMapping(value = "/bus/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> busUpdate(@RequestBody BusUpdateRequest updateRequest) throws AuthException {
        return doUpdate(BusConfig.class, updateRequest);
    }

    @RequestMapping(value = "/user/update", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> userUpdate(@RequestBody UserUpdateRequest updateRequest) throws AuthException {
        return doUpdate(User.class, updateRequest);
    }

    /**
     * Handle auth errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final AuthException e, HttpServletResponse response) {
        logger.error("Provisioning authentication error: " + e.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return new HashMap<String,String>() {{
            put(ERR_MSG_FIELD, e.getMessage());
        }};
    }

    /**
     * Handle all other errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final Exception e, HttpServletResponse response) {
        logger.error("Error handling provisioning request", bpConfig.getDebugException(e));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return new HashMap<String,String>() {{
            put(ERR_MSG_FIELD, e.getMessage());
        }};
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(ProvisioningController.class);

    private static final String BACKPLANE_UPDATE_SUCCESS = "BACKPLANE_UPDATE_SUCCESS";
    private static final String BACKPLANE_DELETE_SUCCESS = "BACKPLANE_DELETE_SUCCESS";
    private static final String ERR_MSG_FIELD = "ERR_MSG";
    private static final String CONFIG_NOT_FOUND = "CONFIG_NOT_FOUND";

    @Inject
    private BackplaneConfig bpConfig;

    @Inject
    private SuperSimpleDB superSimpleDb;

    private <T extends AbstractMessage> Map<String, Map<String, String>> doList(Class<T> entityType, List<String> entityNames) {

        if (entityNames.size() == 0) return doListAll(entityType);

        final Map<String,Map<String,String>> result = new LinkedHashMap<String, Map<String, String>>();
        for(String entityName : entityNames) {
            T config = null;
            Exception thrown = null;
            try {
                config = bpConfig.getConfig(entityName, entityType);
            } catch (Exception e) {
                thrown = e;
            }
            final String errMgs = thrown != null ? thrown.getMessage() : config == null ? CONFIG_NOT_FOUND : null;

            result.put(entityName,
                errMgs != null ? new HashMap<String, String>() {{ put(ERR_MSG_FIELD, errMgs); }} :
                config);
        }
        return result;
    }

    private <T extends AbstractMessage> Map<String, Map<String, String>> doListAll(Class<T> entityType) {
        Map<String,Map<String,String>> result = new LinkedHashMap<String, Map<String, String>>();
        try {
            for(T config :  superSimpleDb.retrieve(bpConfig.getTableNameForType(entityType), entityType)) {
                result.put(config.getIdValue(), config);
            }
        } catch (final Exception e) {
            result.put(ERR_MSG_FIELD, new HashMap<String, String>() {{ put(ERR_MSG_FIELD, e.getMessage()); }});
        }
        return result;
    }

    private <T extends AbstractMessage> Map<String, String> doDelete(Class<T> entityType, List<String> entityNames) {
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(String entityName : entityNames) {
            String deleteStatus = BACKPLANE_DELETE_SUCCESS;
            try {
                superSimpleDb.delete(bpConfig.getTableNameForType(entityType), entityName);
            } catch (Exception e) {
                deleteStatus = e.getMessage();
            }
            result.put(entityName, deleteStatus);
        }
        return result;
    }

    private <T extends AbstractMessage> Map<String, String> doUpdate(Class<T> entityType, UpdateRequest<T> updateRequest) throws AuthException {
        bpConfig.checkAdminAuth(updateRequest.getAdmin(), updateRequest.getSecret());
        validateConfigs(entityType, updateRequest);
        return updateConfigs(entityType, updateRequest.getConfigs());
    }

    private <T extends AbstractMessage> void validateConfigs(Class<T> entityType, UpdateRequest<T> updateRequest) {
        for(T config : updateRequest.getConfigs()) {
            config.validate();
        }
    }

    private <T extends AbstractMessage> Map<String, String> updateConfigs(Class<T> customerConfigType, List<T> bpConfigs) {
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(T config : bpConfigs) {
            if (config instanceof User) {
                // hash the new user password
                User user = (User) config;
                user.put(User.Field.PWDHASH.getFieldName(), HmacHashUtils.hmacHash(user.get(User.Field.PWDHASH)));
            }
            String updateStatus = BACKPLANE_UPDATE_SUCCESS;
            try {
                superSimpleDb.store(bpConfig.getTableNameForType(customerConfigType), customerConfigType, config);
            } catch (Exception e) {
                updateStatus = e.getMessage();
            }
            result.put(config.getIdValue(), updateStatus);
        }
        return result;
    }

    // type helper classes for JSON mapper
    private static class BusUpdateRequest extends UpdateRequest<BusConfig> {}
    private static class UserUpdateRequest extends UpdateRequest<User> {}
}
