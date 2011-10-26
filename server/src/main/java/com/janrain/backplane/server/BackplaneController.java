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

import com.janrain.backplane.server.config.AuthException;
import com.janrain.backplane.server.config.BackplaneConfig;
import com.janrain.backplane.server.config.BusConfig;
import com.janrain.backplane.server.config.User;
import com.janrain.backplane.server.metrics.MetricsAccumulator;
import com.janrain.crypto.HmacHashUtils;
import com.janrain.simpledb.SimpleDBException;
import com.janrain.simpledb.SuperSimpleDB;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.HistogramMetric;
import com.yammer.metrics.core.MeterMetric;
import com.yammer.metrics.core.TimerMetric;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Backplane API implementation.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/*")
@SuppressWarnings({"UnusedDeclaration"})
public class BackplaneController {

    // - PUBLIC

    @RequestMapping(value = "/", method = { RequestMethod.GET, RequestMethod.HEAD })
    public ModelAndView greetings(HttpServletRequest request, HttpServletResponse response) {
        if (RequestMethod.HEAD.toString().equals(request.getMethod())) {
            response.setContentLength(0);
        }
        return new ModelAndView("welcome");
    }

    @RequestMapping(value = "/bus/{bus}", method = RequestMethod.GET)
    public @ResponseBody List<HashMap<String,Object>> getBusMessages(
                                @RequestHeader(value = "Authorization", required = false) String basicAuth,
                                @PathVariable String bus,
                                @RequestParam(value = "since", defaultValue = "") String since,
                                @RequestParam(value = "sticky", required = false) String sticky )
        throws AuthException, SimpleDBException, BackplaneServerException {

        checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.GETALL);

        // log metric
        busGets.mark();

        if (! StringUtils.isBlank(sticky) &&  "true".equalsIgnoreCase(sticky)) {
            busGetsSticky.mark();
        }

        StringBuilder whereClause = new StringBuilder()
            .append(BackplaneMessage.Field.BUS.getFieldName()).append("='").append(bus).append("'");
        if (! StringUtils.isEmpty(since)) {
            whereClause.append(" and ").append(BackplaneMessage.Field.ID.getFieldName()).append(" > '").append(since).append("'");
        }
        if (! StringUtils.isEmpty(sticky)) {
            whereClause.append(" and ").append(BackplaneMessage.Field.STICKY.getFieldName()).append("='").append(sticky).append("'");
        }

        List<BackplaneMessage> messages = superSimpleDb.retrieveWhere(bpConfig.getMessagesTableName(), BackplaneMessage.class, whereClause.toString(), true);

        List<HashMap<String,Object>> frames = new ArrayList<HashMap<String, Object>>();
        for (BackplaneMessage message : messages) {
            frames.add(message.asFrame());
        }
        return frames;

    }

    @RequestMapping(value = "/bus/{bus}/channel/{channel}", method = RequestMethod.GET)
    public ResponseEntity<String> getChannel(
                                @PathVariable String bus,
                                @PathVariable String channel,
                                @RequestParam(required = false) String callback,
                                @RequestParam(value = "since", required = false) String since,
                                @RequestParam(value = "sticky", required = false) String sticky )
        throws SimpleDBException, AuthException, BackplaneServerException {

        // log metric
        channelGets.mark();

        if (! StringUtils.isBlank(sticky) &&  "true".equalsIgnoreCase(sticky)) {
            channelGetsSticky.mark();
        }

        if (StringUtils.isBlank(callback)) {
            return new ResponseEntity<String>(
                    NEW_CHANNEL_LAST_PATH.equals(channel) ? newChannel() : getChannelMessages(bus, channel, since, sticky),
                    new HttpHeaders() {{
                        add("Content-Type", "application/json");
                    }},
                    HttpStatus.OK);

        } else {
            return new ResponseEntity<String>(
                    paddedResponse(callback, NEW_CHANNEL_LAST_PATH.equals(channel) ? newChannel() : getChannelMessages(bus, channel, since, sticky)),
                    new HttpHeaders() {{
                        add("Content-Type", "application/x-javascript");
                    }},
                    HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/bus/{bus}/channel/{channel}", method = RequestMethod.POST)
    public @ResponseBody String postToChannel(
                                @RequestHeader(value = "Authorization", required = false) String basicAuth,
                                @RequestBody List<Map<String,Object>> messages,
                                @PathVariable String bus,
                                @PathVariable String channel) throws AuthException, SimpleDBException, BackplaneServerException {
        checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.POST);

        //Block post if the caller has exceeded the message post limit
        Long count = superSimpleDb.retrieveCount(bpConfig.getMessagesTableName(),
                "select count(*) from `" + bpConfig.getMessagesTableName() + "` where bus='" + bus + "' and channel_name='" + channel + "'");

        if (count >= bpConfig.getDefaultMaxMessageLimit()) {
            throw new BackplaneServerException("Message limit exceeded for this channel");
        }

        //log metric - although this metric may need to be seeded on instance startup to be accurate
        messagesPerChannel.update(count);

        //log metric
        posts.mark();

        for(Map<String,Object> messageData : messages) {
            BackplaneMessage message = new BackplaneMessage(generateMessageId(), bus, channel, messageData);
            superSimpleDb.store(bpConfig.getMessagesTableName(), BackplaneMessage.class, message, true); // todo: make long entries support configurable
        }

        return "";
    }

    /**
     * Handle auth errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final AuthException e, HttpServletResponse response) {
        logger.error("Backplane authentication error: " + bpConfig.getDebugException(e));
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
        logger.error("Error handling backplane request", bpConfig.getDebugException(e));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return new HashMap<String,String>() {{
            try {
                put(ERR_MSG_FIELD, bpConfig.isDebugMode() ? e.getMessage() : "Error processing request.");
            } catch (SimpleDBException e1) {
                put(ERR_MSG_FIELD, "Error processing request.");
            }
        }};
    }

    /**
     * @return a time-based, lexicographically comparable message ID.
     */
    public static String generateMessageId() {
        return BackplaneConfig.ISO8601.format(new Date()) + "-" + randomString(10);
    }

    public static String randomString(int length) {
        byte[] randomBytes = new byte[length];
        random.nextBytes(randomBytes);
        for (int i = 0; i < length; i++) {
            byte b = randomBytes[i];
            int c = Math.abs(b % 16);
            if (c < 10) c += 48; // map (0..9) to '0' .. '9'
            else c += (97 - 10);   // map (10..15) to 'a'..'f'
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
    
    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneController.class);

    private static final String NEW_CHANNEL_LAST_PATH = "new";
    private static final String ERR_MSG_FIELD = "ERR_MSG";
    private static final int CHANNEL_NAME_LENGTH = 32;

    private final MeterMetric posts =
            Metrics.newMeter(BackplaneController.class, "post", "posts", TimeUnit.MINUTES);

    private final MeterMetric channelGets =
            Metrics.newMeter(BackplaneController.class, "channel_get", "channel_gets", TimeUnit.MINUTES);
    private final MeterMetric channelGetsSticky = Metrics.newMeter(BackplaneController.class, "channel_gets_sticky", "channel_gets_sticky", TimeUnit.MINUTES);


    private final MeterMetric busGets =
            Metrics.newMeter(BackplaneController.class, "bus_get", "bus_gets", TimeUnit.MINUTES);
    private final MeterMetric busGetsSticky = Metrics.newMeter(BackplaneController.class, "bus_gets_sticky", "bus_gets_sticky", TimeUnit.MINUTES);

    private final TimerMetric getMessagesTime =
            Metrics.newTimer(BackplaneController.class, "get_messages_time", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);

    private final HistogramMetric payLoadSizesOnGets = Metrics.newHistogram(BackplaneController.class, "payload_sizes_gets");

    private final HistogramMetric messagesPerChannel = Metrics.newHistogram(BackplaneController.class, "messages_per_channel");

    @Inject
    private BackplaneConfig bpConfig;

    @Inject
    private SuperSimpleDB superSimpleDb;

    @Inject
    private MetricsAccumulator metricAccumulator;

    private static final Random random = new SecureRandom();

    private void checkAuth(String basicAuth, String bus, BackplaneConfig.BUS_PERMISSION permission) throws AuthException {
        // authN
        String userPass = null;
        if ( basicAuth == null || ! basicAuth.startsWith("Basic ") || basicAuth.length() < 7) {
            authError("Invalid Authorization header: " + basicAuth);
        } else {
            try {
                userPass = new String(Base64.decodeBase64(basicAuth.substring(6).getBytes("utf-8")));
            } catch (UnsupportedEncodingException e) {
                authError("Cannot check authentication, unsupported encoding: utf-8"); // shouldn't happen
            }
        }

        @SuppressWarnings({"ConstantConditions"})
        int delim = userPass.indexOf(":");
        if (delim == -1) {
            authError("Invalid Basic auth token: " + userPass);
        }
        String user = userPass.substring(0, delim);
        String pass = userPass.substring(delim + 1);

        User userEntry = null;
        try {
            userEntry = bpConfig.getConfig(user, User.class);
        } catch (SimpleDBException e) {
            authError("Error looking up user: " + user);
        }

        if (userEntry == null) {
            authError("User not found: " + user);
        } else if ( ! HmacHashUtils.checkHmacHash(pass, userEntry.get(User.Field.PWDHASH)) ) {
            authError("Incorrect password for user " + user);
        }

        // authZ
        BusConfig busConfig = null;
        try {
            busConfig = bpConfig.getConfig(bus, BusConfig.class);
        } catch (SimpleDBException e) {
            authError("Error looking up bus configuration for " + bus);
        }
        if (busConfig == null) {
            authError("Bus configuration not found for " + bus);
        } else if (!busConfig.getPermissions(user).contains(permission)) {
            logger.error("User " + user + " denied " + permission + " to " + bus);
            throw new AuthException("Access denied.");
        }
    }

    private void authError(String errMsg) throws AuthException {
        logger.error(errMsg);
        try {
            throw new AuthException("Access denied. " + (bpConfig.isDebugMode() ? errMsg : ""));
        } catch (Exception e) {
            throw new AuthException("Access denied.");
        }
    }

    private String paddedResponse(String callback, String s) {
        if (StringUtils.isBlank(callback)) {
            throw new IllegalArgumentException("Callback cannot be blank.");
        }
        StringBuilder result = new StringBuilder(callback);
        result.append("(").append(s).append(")");
        return result.toString();
    }

    private String newChannel() {
        return "\"" + randomString(CHANNEL_NAME_LENGTH) +"\"";
    }

    private String getChannelMessages(final String bus, final String channel, final String since, final String sticky) throws SimpleDBException, BackplaneServerException {

        try {
            return getMessagesTime.time(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    StringBuilder whereClause = new StringBuilder()
                        .append(BackplaneMessage.Field.BUS.getFieldName()).append("='").append(bus).append("'")
                        .append(" and ").append(BackplaneMessage.Field.CHANNEL_NAME.getFieldName()).append("='").append(channel).append("'");
                    if (! StringUtils.isEmpty(since)) {
                        whereClause.append(" and ").append(BackplaneMessage.Field.ID.getFieldName()).append(" > '").append(since).append("'");
                    }
                    if (! StringUtils.isEmpty(sticky)) {
                        whereClause.append(" and ").append(BackplaneMessage.Field.STICKY.getFieldName()).append("='").append(sticky).append("'");
                    }

                    List<BackplaneMessage> messages = superSimpleDb.retrieveWhere(bpConfig.getMessagesTableName(), BackplaneMessage.class, whereClause.toString(), true);

                    List<Map<String,Object>> frames = new ArrayList<Map<String, Object>>();

                    for (BackplaneMessage message : messages) {
                        frames.add(message.asFrame());
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        String payload = mapper.writeValueAsString(frames);
                        payLoadSizesOnGets.update(payload.length());
                        return mapper.writeValueAsString(frames);
                    } catch (IOException e) {
                        String errMsg = "Error converting frames to JSON: " + e.getMessage();
                        logger.error(errMsg, bpConfig.getDebugException(e));
                        throw new BackplaneServerException(errMsg, e);
                    }
                }
            });
        } catch (SimpleDBException sdbe) {
            throw sdbe;
        } catch (BackplaneServerException bse) {
            throw bse;
        } catch (Exception e) {
            throw new BackplaneServerException(e.getMessage());
        }
    }
}
