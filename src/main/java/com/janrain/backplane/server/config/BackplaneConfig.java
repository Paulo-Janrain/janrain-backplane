package com.janrain.backplane.server.config;

import com.janrain.backplane.server.ApplicationException;
import com.janrain.backplane.server.provision.UserEntry;
import com.janrain.backplane.server.provision.PermEntry;
import com.janrain.message.AbstractMessage;
import com.janrain.message.AbstractNamedMap;
import com.janrain.message.NamedMap;
import com.janrain.simpledb.SimpleDBException;
import com.janrain.simpledb.SuperSimpleDB;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.janrain.backplane.server.MessageFrame.Field.CHANNEL_ID;
import static com.janrain.backplane.server.MessageFrame.Field.MESSAGE_ID;
import static com.janrain.backplane.server.config.BusConfig.Field.BUS_ID;
import static com.janrain.backplane.server.config.BusConfig.Field.RETENTION_TIME_SECONDS;
import static com.janrain.backplane.server.provision.PermEntry.Field.*;


/**
 * Holds configuration settings for the SSO server
 * 
 * @author Jason Cowley, Johnny Bufu
 */
@Scope(value="singleton")
public class BackplaneConfig {

    // - PUBLIC

    public enum BUS_PERMISSION {
        GETALL, POST, IDENTITY
    }

    public void checkAdminAuth(String user, String password) throws AuthException {
        checkAuth(getAdminAuthTableName(), user, password);
    }

    public void checkBackplaneAuth(String bus, BUS_PERMISSION perm, String user, String password) throws AuthException {
        checkAuth(getTableNameForType(UserEntry.class), user, password);
        checkPerm(bus, user, perm);
   }

    public <T extends NamedMap> String getTableNameForType(Class<T> type) {
        return bpInstanceId + "_" + type.getSimpleName();
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
            throw new ApplicationException("Error looking up " + entityType.getSimpleName() + " configuration for " + entityName);
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
    private static final String BP_ADMIN_AUTH_TABLE_SUFFIX = "_admin";
    private static final String BP_CONFIG_ENTRY_NAME = "bpserverconfig";
    private static final String BP_MESSAGES_TABLE_SUFFIX = "_messages";

    private final String bpInstanceId;
    private final ScheduledExecutorService cleanup;


    private static enum BpServerProperty {
        DEBUG_MODE,
        CONFIG_CACHE_AGE_SECONDS,
        CLEANUP_INTERVAL_MINUTES
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private BackplaneConfig() {
        this.bpInstanceId = getAwsProp(BP_AWS_INSTANCE_ID);
        logger.info("Configured Backplane Server instance: " + bpInstanceId);
        this.cleanup = createCleanupTask();
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
                    simpleDb.deleteWhere(messagesTable, getExpiredMessagesClause(busConfig.get(BUS_ID), busConfig.get(RETENTION_TIME_SECONDS)));
                } catch (SimpleDBException sdbe) {
                    logger.error("Error cleaning up expired messages on bus "  + busConfig.get(BUS_ID) + ", " + sdbe.getMessage(), sdbe);
                }
            }
        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Callback cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Callback cleanup task finished.");
        }
    }

    private String getExpiredMessagesClause(String busId, String retentionTimeSeconds) {
        return "where " +
            CHANNEL_ID.getFieldName() + " like '" + busId + "%' AND " +
            MESSAGE_ID.getFieldName() + " < '" + Long.toString(System.currentTimeMillis() - Long.valueOf(retentionTimeSeconds) * 1000) + "'";
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

    private String getMessagesTableName() {
        return bpInstanceId + BP_MESSAGES_TABLE_SUFFIX;
    }

    private Long getMaxCacheAge() {
        return bpServerConfigCache != null && bpServerConfigCache.left != null ?
            Long.valueOf(bpServerConfigCache.left.get(BpServerProperty.CONFIG_CACHE_AGE_SECONDS.name())) :
            null;
    }

    private void checkAuth(String authTable, String user, String password) throws AuthException {
        try {
            UserEntry userEntry = simpleDb.retrieve(authTable, UserEntry.class, user);
            String authKey = userEntry == null ? null : userEntry.get(UserEntry.Field.PWDHASH);
            // todo: hash secret
            if (authKey == null || ! authKey.equals(password)) {
                throw new AuthException("User  " + user + " not authorized in " + authTable);
            }
        } catch (SimpleDBException e) {
            throw new AuthException("User  " + user + " not authorized in " + authTable + " , " + e.getMessage(), e);
        }
    }

    private void checkPerm(String bus, String user, BUS_PERMISSION perm) throws AuthException {
        String permissionsTable = getTableNameForType(PermEntry.class);
        try {
            StringBuilder whereClause = new StringBuilder()
                .append(USER.getFieldName()).append(" = '").append(user).append("' AND ")
                .append(BUS.getFieldName()).append(" = '").append(bus).append("' AND ")
                .append(PERM.getFieldName()).append(" = '").append(perm.name()).append("'");
            List<?> permissions = simpleDb.retrieveWhere(permissionsTable, PermEntry.class, whereClause.toString());
            if (permissions == null || permissions.isEmpty()) {
                throw new AuthException("User  " + user + " not authorized for  [" + bus + ":" + perm + "]");
            }
        } catch (SimpleDBException e) {
            throw new AuthException("User  " + user + " not authorized for  [" + bus + ":" + perm + "]" + " , " + e.getMessage(), e);
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
