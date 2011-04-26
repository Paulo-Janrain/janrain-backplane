package com.janrain.backplane.server;

import com.janrain.backplane.server.config.AuthException;
import com.janrain.backplane.server.config.BackplaneConfig;
import com.janrain.backplane.server.config.BusConfig;
import com.janrain.backplane.server.provision.UserEntry;
import com.janrain.simpledb.SimpleDBException;
import com.janrain.simpledb.SuperSimpleDB;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Backplane API implementation.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/v1/bus/*")
@SuppressWarnings({"UnusedDeclaration"})
public class BackplaneController {

    // - PUBLIC

    @RequestMapping(value = "/{bus}/new_channel", method = RequestMethod.GET)
    public String newChannel() {
        return randomString(CHANNEL_NAME_LENGTH);
    }

    @RequestMapping(value = "/{bus}", method = RequestMethod.GET)
    public @ResponseBody List<BackplaneFrame> getBusMessages( @RequestHeader(value = "Authorization") String basicAuth,
                                  @PathVariable String bus,
                                  @RequestParam(value = "since", defaultValue = "") String since) throws AuthException, SimpleDBException {
        checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.GETALL);

        StringBuilder whereClause = new StringBuilder()
            .append(BackplaneMessage.Field.BUS.getFieldName()).append("=").append(bus);
        if (since.length() > 0) {
            whereClause.append(BackplaneMessage.Field.ID).append(" > ").append(since);
        }

        List<BackplaneMessage> messages = simpleDb.retrieveWhere(bpConfig.getMessagesTableName(), BackplaneMessage.class, whereClause.toString());
        List<BackplaneFrame> frames = new ArrayList<BackplaneFrame>();
        for (BackplaneMessage message : messages) {
            frames.add(new BackplaneFrame(message, true));
        }
        return frames;
    }

    @RequestMapping(value = "/{bus}/channel/{channel}", method = RequestMethod.GET)
    public @ResponseBody List<BackplaneFrame> getChannelMessages(@PathVariable String bus,
                                     @PathVariable String channel,
                                     @RequestParam(value = "since", required = false) String since,
                                     @RequestHeader(value = "Authorization", required = false) String basicAuth) throws SimpleDBException, AuthException {

        if (basicAuth != null) {
            checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.GETPAYLOAD);
        }

        StringBuilder whereClause = new StringBuilder()
            .append(BackplaneMessage.Field.BUS.getFieldName()).append("=").append(bus)
            .append(BackplaneMessage.Field.CHANNEL_NAME).append("=").append(channel);
        if (since.length() > 0) {
            whereClause.append(BackplaneMessage.Field.ID).append(" > ").append(since);
        }

        List<BackplaneMessage> messages = simpleDb.retrieveWhere(bpConfig.getMessagesTableName(), BackplaneMessage.class, whereClause.toString());
        List<BackplaneFrame> frames = new ArrayList<BackplaneFrame>();
        for (BackplaneMessage message : messages) {
            frames.add(new BackplaneFrame(message, basicAuth != null));
        }
        return frames;
    }

    @RequestMapping(value = "/{bus}/channel/{channel}", method = RequestMethod.POST)
    public String postToChannel( @RequestHeader(value = "Authorization") String basicAuth, @RequestBody List<BackplaneMessage> messages,
                                 @PathVariable String bus, @PathVariable String channel) throws AuthException, SimpleDBException {
        checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.POST);

        for(BackplaneMessage message : messages) {
            message.setId(generateMessageId());
            message.setBus(bus);
            message.setChannelName(channel);
            simpleDb.store(bpConfig.getMessagesTableName(), BackplaneMessage.class, message);
        }

        return "";
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneController.class);

    private static final int CHANNEL_NAME_LENGTH = 32;

    @Inject
    private BackplaneConfig bpConfig;

    @Inject
    private SuperSimpleDB simpleDb;

    private static final Random random = new SecureRandom();

    private static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") {{
        setTimeZone(TimeZone.getTimeZone("GMT"));
    }};

    /**
     * @return a time-based, lexicographically comparable message ID.
     */
    private static String generateMessageId() {
        return ISO8601.format(new Date()) + "-" + randomString(10);
    }

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

        UserEntry userEntry = null;
        try {
            userEntry = bpConfig.getConfig(user, UserEntry.class);
        } catch (SimpleDBException e) {
            authError("Error looking up user: " + user);
        }

        if (userEntry == null) {
            authError("User not found: " + user);
        } else if (! userEntry.get(UserEntry.Field.PWDHASH).equals(getHash(pass))) {
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

    private String getHash(String pass) {
        // todo: HMAC hash
        return pass;
    }

    private void authError(String errMsg) throws AuthException {
        logger.error(errMsg);
        try {
            throw new AuthException("Access denied. " + (bpConfig.isDebugMode() ? errMsg : ""));
        } catch (Exception e) {
            throw new AuthException("Access denied.");
        }
    }
}
