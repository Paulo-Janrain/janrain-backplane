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

import com.janrain.backplane.server.ApplicationException;
import com.janrain.crypto.HmacHashUtils;
import com.janrain.message.AbstractMessage;
import com.janrain.message.AbstractNamedMap;
import com.janrain.message.NamedMap;
import com.janrain.simpledb.SimpleDBException;
import com.janrain.simpledb.SuperSimpleDB;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.janrain.backplane.server.BackplaneMessage.Field.CHANNEL_NAME;
import static com.janrain.backplane.server.BackplaneMessage.Field.ID;
import static com.janrain.backplane.server.config.BusConfig.Field.BUS_NAME;
import static com.janrain.backplane.server.config.BusConfig.Field.RETENTION_TIME_SECONDS;


/**
 * Holds configuration settings for the Backplane server
 * 
 * @author Jason Cowley, Johnny Bufu
 */
@Scope(value="singleton")
public class BackplaneConfig {

    // - PUBLIC

    public enum BUS_PERMISSION { GETALL, POST, GETPAYLOAD, IDENTITY }

    public void checkAdminAuth(String user, String password) throws AuthException {
        checkAuth(getAdminAuthTableName(), user, password);
    }

    public <T extends NamedMap> String getTableNameForType(Class<T> type) {
        return bpInstanceId + "_" + type.getSimpleName();
    }

    public String getMessagesTableName() {
        return bpInstanceId + BP_MESSAGES_TABLE_SUFFIX;
    }
    
    /**
     * Retrieve a configuration entity by its name
     *
     * @param entityName	The entity name for the configuration
     * @return		        The entity configuration
     * @throws ApplicationException if no matching entity configuration is found
     */
    public <T extends AbstractMessage> T getConfig(String entityName, Class<T> entityType) throws SimpleDBException {
        T config = simpleDb.retrieve(getTableNameForType(entityType), entityType, entityName);
        if (config == null) {
            throw new ApplicationException("Error looking up " + entityType.getSimpleName() + " " + entityName);
        }
        return config;
    }


    /**
	 * @return the debugMode
	 */
	public boolean isDebugMode() throws SimpleDBException {
		return Boolean.valueOf(cachedGet(BpServerProperty.DEBUG_MODE));
	}

    public Exception getDebugException(Exception e) {
        try {
            return isDebugMode() ? e : null;
        } catch (SimpleDBException sdbe) {
            logger.error("Error getting debug mode", sdbe); // shouldn't happen
            return e;
        }
    }

    // - PACKAGE

    static String getAwsEnv(String envParamName) {
        String result = System.getenv(envParamName);
        if (StringUtils.isBlank(result)) {
            throw new RuntimeException("Required environment configuration missing: " + envParamName);
        }
        return result;
    }

    static String getAwsProp(String propParamName) {
        String result = System.getProperty(propParamName);
        if (StringUtils.isBlank(result)) {
            throw new RuntimeException("Required system property configuration missing: " + propParamName);
        }
        return result;
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneConfig.class);

    private static final String BP_AWS_INSTANCE_ID = "PARAM1";
    private static final String BP_SERVER_CONFIG_TABLE_SUFFIX = "_bpserverconfig";
    private static final String BP_ADMIN_AUTH_TABLE_SUFFIX = "_Admin";
    private static final String BP_CONFIG_ENTRY_NAME = "bpserverconfig";
    private static final String BP_MESSAGES_TABLE_SUFFIX = "_messages";

    private final String bpInstanceId;
    private ScheduledExecutorService cleanup;


    private static enum BpServerProperty {
        DEBUG_MODE,
        CONFIG_CACHE_AGE_SECONDS,
        CLEANUP_INTERVAL_MINUTES
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private BackplaneConfig() {
        this.bpInstanceId = getAwsProp(BP_AWS_INSTANCE_ID);
        logger.info("Configured Backplane Server instance: " + bpInstanceId);
    }

    private ScheduledExecutorService createCleanupTask() {
        long cleanupIntervalMinutes;
        try {
            cleanupIntervalMinutes = Long.valueOf(cachedGet(BpServerProperty.CLEANUP_INTERVAL_MINUTES));
        } catch (SimpleDBException e) {
            throw new RuntimeException("Error getting server property " + BpServerProperty.CLEANUP_INTERVAL_MINUTES, e);
        }

        ScheduledExecutorService cleanupTask = Executors.newScheduledThreadPool(1);
        cleanupTask.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                deleteExpiredMessages();
            }

        }, cleanupIntervalMinutes, cleanupIntervalMinutes, TimeUnit.MINUTES);
        return cleanupTask;
    }

    @PostConstruct
    private void init() {
        this.cleanup = createCleanupTask();
    }

    @PreDestroy
    private void cleanup() {
        this.cleanup.shutdownNow();
    }

    private void deleteExpiredMessages() {
        try {
            logger.info("Backplane message cleanup task started.");
            String messagesTable = getMessagesTableName();
            for(BusConfig busConfig : simpleDb.retrieve(getTableNameForType(BusConfig.class), BusConfig.class)) {
                try {
                    simpleDb.deleteWhere(messagesTable, getExpiredMessagesClause(busConfig.get(BUS_NAME), busConfig.get(RETENTION_TIME_SECONDS)));
                } catch (SimpleDBException sdbe) {
                    logger.error("Error cleaning up expired messages on bus "  + busConfig.get(BUS_NAME) + ", " + sdbe.getMessage(), sdbe);
                }
            }
        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Backplane messages cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Backplane messages cleanup task finished.");
        }
    }

    private String getExpiredMessagesClause(String busId, String retentionTimeSeconds) {
        return CHANNEL_NAME.getFieldName() + " like '" + busId + "%' AND " +
               ID.getFieldName() + " < '" + Long.toString(System.currentTimeMillis() - Long.valueOf(retentionTimeSeconds) * 1000) + "'";
    }

    @Inject
    @SuppressWarnings({"UnusedDeclaration"})
    private SuperSimpleDB simpleDb;

    private Pair<BpServerConfigMap,Long> bpServerConfigCache;

    private String cachedGet(BpServerProperty property) throws SimpleDBException {
        Pair<BpServerConfigMap,Long> result = bpServerConfigCache;
        Long maxCacheAge = getMaxCacheAge();
        if (result == null || result.left == null || result.right == null || maxCacheAge == null ||
            result.right + maxCacheAge < System.currentTimeMillis() ) {
            synchronized (this) {
                result = bpServerConfigCache;
                if (result == null || result.left == null || result.right == null ||  maxCacheAge == null ||
                    result.right + maxCacheAge < System.currentTimeMillis() ) {
                    result = new Pair<BpServerConfigMap, Long>(simpleDb.retrieve(getBpServerConfigTableName(), BpServerConfigMap.class, BP_CONFIG_ENTRY_NAME), System.currentTimeMillis());
                    bpServerConfigCache = result;
                }
            }
        }
        return result.left == null ? null : result.left.get(property.name());
    }

    private String getBpServerConfigTableName() {
        return bpInstanceId + BP_SERVER_CONFIG_TABLE_SUFFIX;
    }

    private String getAdminAuthTableName() {
        return bpInstanceId + BP_ADMIN_AUTH_TABLE_SUFFIX;
    }

    private Long getMaxCacheAge() {
        return bpServerConfigCache != null && bpServerConfigCache.left != null ?
            Long.valueOf(bpServerConfigCache.left.get(BpServerProperty.CONFIG_CACHE_AGE_SECONDS.name())) :
            null;
    }

    private void checkAuth(String authTable, String user, String password) throws AuthException {
        try {
            User userEntry = simpleDb.retrieve(authTable, User.class, user);
            String authKey = userEntry == null ? null : userEntry.get(User.Field.PWDHASH);
            if ( ! HmacHashUtils.checkHmacHash(password, authKey) ) {
                throw new AuthException("User " + user + " not authorized in " + authTable);
            }
        } catch (SimpleDBException e) {
            throw new AuthException("User " + user + " not authorized in " + authTable + " , " + e.getMessage(), e);
        }
    }
    public static class BpServerConfigMap extends AbstractNamedMap {

        @SuppressWarnings({"UnusedDeclaration"}) // instantiation through reflection
        public BpServerConfigMap() { }

        @Override
        public void setName(String name) { }

        @Override
        public String getName() { return BP_CONFIG_ENTRY_NAME; }
    }

    private static class Pair<L,R> {
        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        public final L left;
        public final R right;
    }
}
