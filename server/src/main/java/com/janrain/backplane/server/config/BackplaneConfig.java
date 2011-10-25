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
import com.janrain.backplane.server.metrics.MetricMessage;
import com.janrain.backplane.server.metrics.MetricsAccumulator;
import com.janrain.crypto.HmacHashUtils;
import com.janrain.message.AbstractMessage;
import com.janrain.message.AbstractNamedMap;
import com.janrain.message.NamedMap;
import com.janrain.simpledb.SimpleDBException;
import com.janrain.simpledb.SuperSimpleDB;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.TimerMetric;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.janrain.backplane.server.BackplaneMessage.Field.*;
import static com.janrain.backplane.server.config.BusConfig.Field.*;


/**
 * Holds configuration settings for the Backplane server
 * 
 * @author Jason Cowley, Johnny Bufu
 */
@Scope(value="singleton")
public class BackplaneConfig {

    // - PUBLIC

    public enum BUS_PERMISSION { GETALL, POST, GETPAYLOAD, IDENTITY }

    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") {{
        setTimeZone(TimeZone.getTimeZone("GMT"));
    }};

    public void checkAdminAuth(String user, String password) throws AuthException {
        checkAuth(getAdminAuthTableName(), user, password);
    }

    public void checkMetricAuth(String user, String password) throws AuthException {
        checkAuth(getMetricAuthTableName(), user, password);
    }

    public <T extends NamedMap> String getTableNameForType(Class<T> type) {
        return bpInstanceId + "_" + type.getSimpleName();
    }

    public String getMetricsTableName() {
        return bpInstanceId +  BP_METRICS_TABLE_SUFFIX;
    }

    public String getMessagesTableName() {
        return bpInstanceId + BP_MESSAGES_TABLE_SUFFIX;
    }

    public String getSamplesTableName() {
        return bpInstanceId + BP_SAMPLES_TABLE_SUFFIX;
    }
    
    /**
     * Retrieve a configuration entity by its name
     *
     *
     * @param entityName	The entity name for the configuration
     * @return		        The entity configuration
     * @throws ApplicationException if no matching entity configuration is found
     */
    public <T extends AbstractMessage> T getConfig(String entityName, Class<T> entityType) throws SimpleDBException {
        T config = superSimpleDb.retrieve(getTableNameForType(entityType), entityType, entityName);
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

    /**
     * @return the server default max message value per channel
     * @throws SimpleDBException
     */

    public long getDefaultMaxMessageLimit() throws SimpleDBException {
        Long max = Long.valueOf(cachedGet(BpServerProperty.DEFAULT_MESSAGES_MAX));
        return max == null ? BackplaneConfig.BP_MAX_MESSAGES_DEFAULT : max;
    }

    public Exception getDebugException(Exception e) {
        try {
            return isDebugMode() ? e : null;
        } catch (SimpleDBException sdbe) {
            logger.error("Error getting debug mode", sdbe); // shouldn't happen
            return e;
        }
    }

    public String getInstanceId() {
        return bpInstanceId;
    }

    public String getBuildVersion() {
        return buildProperties.getProperty(BUILD_VERSION_PROPERTY);
    }

    // - PACKAGE


    BackplaneConfig(String instanceId) {
        this.bpInstanceId = instanceId;
    }

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

    private static final String BUILD_PROPERTIES = "/build.properties";
    private static final String BUILD_VERSION_PROPERTY = "build.version";
    private static final Properties buildProperties = new Properties();


    private static final String BP_AWS_INSTANCE_ID = "PARAM1";
    private static final String BP_SERVER_CONFIG_TABLE_SUFFIX = "_bpserverconfig";
    private static final String BP_ADMIN_AUTH_TABLE_SUFFIX = "_Admin";
    private static final String BP_CONFIG_ENTRY_NAME = "bpserverconfig";
    private static final String BP_MESSAGES_TABLE_SUFFIX = "_messages";
    private static final String BP_SAMPLES_TABLE_SUFFIX = "_samples";
    private static final String BP_METRICS_TABLE_SUFFIX = "_metrics";
    private static final String BP_METRIC_AUTH_TABLE_SUFFIX = "_bpMetricAuth";
    private static final long BP_MAX_MESSAGES_DEFAULT = 100;

    private final String bpInstanceId;
    private ScheduledExecutorService cleanup;

    private final TimerMetric getMessagesTime =
            Metrics.newTimer(BackplaneConfig.class, "cleanup_messages_time", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);

    private static enum BpServerProperty {
        DEBUG_MODE,
        CONFIG_CACHE_AGE_SECONDS,
        CLEANUP_INTERVAL_MINUTES,
        DEFAULT_MESSAGES_MAX
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private BackplaneConfig() {
        this.bpInstanceId = getAwsProp(BP_AWS_INSTANCE_ID);
        try {
            buildProperties.load(BackplaneConfig.class.getResourceAsStream(BUILD_PROPERTIES));
        } catch (IOException e) {
            String err = "Error loading build properties from " + BUILD_PROPERTIES;
            logger.error(err, e);
            throw new RuntimeException(err, e);
        }

        logger.info("Configured Backplane Server instance: " + bpInstanceId);
    }

    private ScheduledExecutorService createCleanupTask() {
        long cleanupIntervalMinutes;
        logger.info("calling createCleanupTask()");
        try {
            cleanupIntervalMinutes = Long.valueOf(cachedGet(BpServerProperty.CLEANUP_INTERVAL_MINUTES));
        } catch (SimpleDBException e) {
            throw new RuntimeException("Error getting server property " + BpServerProperty.CLEANUP_INTERVAL_MINUTES, e);
        }

        ScheduledExecutorService cleanupTask = Executors.newScheduledThreadPool(1);
        cleanupTask.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {

                compileMetrics();

                try {
                    getMessagesTime.time(new Callable<Object>() {
                        @Override
                        public Object call() throws Exception {
                            deleteExpiredMessages();
                            return null;
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error while cleaning up expired messages, " + e.getMessage(), e);
                }

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
        try {
            this.cleanup.shutdown();
            if (this.cleanup.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.info("Background thread shutdown properly");
            } else {
                this.cleanup.shutdownNow();
                if (!this.cleanup.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("Background thread did not terminate");
                }
            }
            Metrics.defaultRegistry().threadPools().shutdownThreadPools();
        } catch (InterruptedException e) {
            logger.error("cleanup() threw an exception", e);
            this.cleanup.shutdownNow();
            Thread.currentThread().interrupt();

        }
    }

    private void compileMetrics() {

        try {
            MetricMessage metric = metricAccumulator.prepareSummary();

            logger.debug("Storing metrics for instance " + MetricsAccumulator.getInstanceUuid());

            // Create the _metrics table if it doesn't already exist.  This is a light-weight call.
            superSimpleDb.checkDomain(getMetricsTableName());

            MetricMessage oldMetric = superSimpleDb.retrieveAndDelete(getMetricsTableName(), MetricMessage.class, metric.getIdValue());

            superSimpleDb.store(getMetricsTableName(), MetricMessage.class, metric, true);

        } catch (Exception e) {
            logger.error("Error compiling metrics " + e.getMessage(), e);
        }

    }

    private void deleteExpiredMessages() {
        try {
            logger.info("Backplane message cleanup task started.");
            String messagesTable = getMessagesTableName();
            for(BusConfig busConfig : superSimpleDb.retrieve(getTableNameForType(BusConfig.class), BusConfig.class)) {
                try {
                    // non-sticky
                    superSimpleDb.deleteWhere(messagesTable, getExpiredMessagesClause(busConfig.get(BUS_NAME), false, busConfig.get(RETENTION_TIME_SECONDS)));
                    // sticky
                    superSimpleDb.deleteWhere(messagesTable, getExpiredMessagesClause(busConfig.get(BUS_NAME), true, busConfig.get(RETENTION_STICKY_TIME_SECONDS)));

                } catch (SimpleDBException sdbe) {
                    logger.error("Error cleaning up expired messages on bus "  + busConfig.get(BUS_NAME) + ", " + sdbe.getMessage(), sdbe);
                }
            }

            try {
                // remove old metrics
                superSimpleDb.deleteWhere(getMetricsTableName(), getExpiredMetricClause());
            } catch (SimpleDBException sdbe) {
                logger.error("Error while removing expired metrics, " + sdbe.getMessage(), sdbe);
            }

        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Backplane messages cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Backplane messages cleanup task finished.");
        }
    }

    private String getExpiredMetricClause() {
        int interval = 0;
        try {
            interval = Integer.valueOf(cachedGet(BpServerProperty.CLEANUP_INTERVAL_MINUTES));
        } catch (SimpleDBException e) {
            throw new RuntimeException("Error getting server property " + BpServerProperty.CLEANUP_INTERVAL_MINUTES, e);
        }
        Calendar now = Calendar.getInstance();
        // Cleanup metrics that may be lingering due to a shutdown server instance
        now.roll(Calendar.MINUTE, -(interval+2));
        return "time < '" + ISO8601.format(now.getTime()) + "'";
    }


    private String getExpiredMessagesClause(String busId, boolean sticky, String retentionTimeSeconds) {
        return BUS.getFieldName() + " = '" + busId + "' AND " +
            // "is (not) null" is low-performance on simpledb apparently
            // http://practicalcloudcomputing.com/post/722621724/simpledb-essentials-for-high-performance-users-part-2
            STICKY.getFieldName() + " = '" + Boolean.toString(sticky) + "' AND " +
            ID.getFieldName() + " < '" +
            ISO8601.format(new Date(System.currentTimeMillis() - Long.valueOf(retentionTimeSeconds) * 1000))
            + "'";
    }

    @Inject
    @SuppressWarnings({"UnusedDeclaration"})
    private SuperSimpleDB superSimpleDb;

    @Inject
    private MetricsAccumulator metricAccumulator;

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
                    result = new Pair<BpServerConfigMap, Long>(superSimpleDb.retrieve(getBpServerConfigTableName(), BpServerConfigMap.class, BP_CONFIG_ENTRY_NAME), System.currentTimeMillis());
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

    private String getMetricAuthTableName() {
        return bpInstanceId + BP_METRIC_AUTH_TABLE_SUFFIX;
    }

    private Long getMaxCacheAge() {
        return bpServerConfigCache != null && bpServerConfigCache.left != null ?
            Long.valueOf(bpServerConfigCache.left.get(BpServerProperty.CONFIG_CACHE_AGE_SECONDS.name())) :
            null;
    }

    public void checkAuth(String authTable, String user, String password) throws AuthException {
        try {
            User userEntry = superSimpleDb.retrieve(authTable, User.class, user);
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
