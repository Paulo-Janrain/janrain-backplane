package com.janrain.simpledb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.*;
import com.janrain.message.NamedMap;
import com.janrain.util.Pair;
import com.janrain.util.Utf8StringUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.*;

/**
 * A class that provides easier access to Amazon SimpleDB data store.
 *
 * todo: workarounds for SimpleDB's limitations if necessary:
 *   Item name length	1024 bytes
 *   Attribute name-value pairs per item	256
 *   Attribute name length	1024 bytes
 *   Attribute value length	1024 bytes
 *   1 billion attributes per domain
 * http://docs.amazonwebservices.com/AmazonSimpleDB/latest/DeveloperGuide/SDBLimits.html
 *
 * @author Johnny Bufu
 */
//@Service(value="superSimpleDB")
@Scope(value="singleton")
public class SuperSimpleDBImpl implements SuperSimpleDB {

    // - PUBLIC

    @Override
    public void create(String table) throws SimpleDBException {
        simpleDB.createDomain(new CreateDomainRequest(table));
        logger.info("SimpleDB created table: " + table);
    }

    @Override
    public <T extends NamedMap> void store(String table, Class<T> type, T data) throws SimpleDBException {
        store(table, type, data, false);
    }

    @Override
    public <T extends NamedMap> void store(String table, Class<T> type, T data, boolean longFields) throws SimpleDBException {
        try {
            checkDomain(table);
            type.cast(data); // enforce runtime type-safety
            simpleDB.putAttributes(new PutAttributesRequest(table, data.getName(), asReplacebleAttributes(data, longFields)));
            logger.info("SimpleDB stored " + table + "/" + data.getName());
        } catch (AmazonClientException e) {
            throw new SimpleDBException("store() threw an exception for domain " + table + ", " + e.getMessage(), e);
        }
    }

    @Override
    public <T extends NamedMap> void update(String table, Class<T> type, T expected, T updated) throws SimpleDBException {
        String accessLockToken = null;
        boolean success = false;
        String key = expected.getName();
        try {
            accessLockToken = accessLock(table, key);
            T fromDB = lockedRetrieve(table, type, key, accessLockToken);
            if (fromDB != null && fromDB.equals(expected)) {
                doDelete(table, key, accessLockToken);
                // todo: not good if simpledb fails in between these two
                store(table, type, updated);
                success = true;
            }
        } finally {
            if (accessLockToken != null && ! success) {
                accessUnlock(table, key, accessLockToken);
            }
        }
    }

    @Override
    public void delete(String table, String key) throws SimpleDBException {

        doDelete(table, key, null);
    }

    @Override
    public void deleteWhere(String table, String whereClause) throws SimpleDBException {
        try {
            List<Item> items = doSelectWhere(table, whereClause, true);
            for (List<DeletableItem> limitedList : asLimitedDeletableItemLists(items) ) {
                simpleDB.batchDeleteAttributes(new BatchDeleteAttributesRequest(table, limitedList));
            }
            logger.info("SimpleDB deleted from " + table + " for query: `" + whereClause + "` " + items.size() + " entries");
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public <T extends NamedMap> T retrieve(String table, Class<T> type, String key) throws SimpleDBException {
        try {
            GetAttributesRequest req = new GetAttributesRequest(table, key).withConsistentRead(true);

            List<Attribute> attributes = simpleDB.getAttributes(req).getAttributes();

            if (attributes.isEmpty()) {
                logger.info("SimpleDB no entry found for " + table + "/" + key);
                return null;
            } else {
                T result = type.newInstance();
                result.init(key, asMap(attributes));
                logger.info("SimpleDB retrieved " + table + "/" + key);
                return result;
            }
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        } catch (InstantiationException e) {
            throw new SimpleDBException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public Long retrieveCount(String table, String query) throws SimpleDBException {
         try {
            checkDomain(table);
            SelectRequest selectRequest = new SelectRequest(query, false);
            SelectResult selectResult= simpleDB.select(selectRequest);
            //the following may need some tweaking to get around the
            //5 second query limit - or, not
            for (Item item : selectResult.getItems()) {
                for (Attribute att : item.getAttributes()) {
                    if ("Count".equals(att.getName()))
                        return Long.valueOf(att.getValue());
                }
            }

            throw new SimpleDBException("retrieveCount failed");
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public <T extends NamedMap> List<T> retrieve(String table, Class<T> type) throws SimpleDBException {
        return retrieveWhere(table, type, null, true);
    }

    @Override
    public <T extends NamedMap> List<T> retrieveWhere(String table, Class<T> type, String whereClause, boolean fetchAllTokens) throws SimpleDBException {
        try {
            List<T> result = new ArrayList<T>();
            for (Item item : doSelectWhere(table, whereClause, fetchAllTokens)) {
                T resultItem = type.newInstance();
                resultItem.init(item.getName(), asMap(item.getAttributes()));
                result.add(resultItem);
            }
            logger.info("SimpleDB retrieved " + result.size() + " entries from " + table +
                (StringUtils.isBlank(whereClause) ? "" : " for query `" + whereClause + "` ") );
            return result;
        } catch (InstantiationException e) {
            throw new SimpleDBException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public <T extends NamedMap> T retrieveAndDelete(String table, Class<T> type, String key) throws SimpleDBException {
        String accessLockToken = null;
        boolean success = false;
        try {
            accessLockToken = accessLock(table, key);
            T result = lockedRetrieve(table, type, key, accessLockToken);
            if (result != null) {
                doDelete(table, key, accessLockToken);
                success = true;
            }
            return result;
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        } finally {
            if (accessLockToken != null && ! success) {
                accessUnlock(table, key, accessLockToken);
            }
        }
    }

    @Override
    public void drop(String table) throws SimpleDBException {
        try {
            simpleDB.deleteDomain(new DeleteDomainRequest(table));
            checkedDomains.remove(table);
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public void checkDomain(String table) {
        if (checkedDomains.contains(table)) return;

        ListDomainsRequest listRequest = new ListDomainsRequest();
        ListDomainsResult domains;
        String nextToken;
        do {
            domains = simpleDB.listDomains(listRequest);
            if (domains.getDomainNames().contains(table)) {
                checkedDomains.add(table);
                return;
            }
            nextToken = domains.getNextToken();
            listRequest.setNextToken(nextToken);

        } while (nextToken != null);

        logger.info("Creating table: " + table);
        simpleDB.createDomain(new CreateDomainRequest(table));
        checkedDomains.add(table);
    }

    // - PACKAGE

    public SuperSimpleDBImpl(AmazonSimpleDB simpleDB) {
        this.simpleDB = simpleDB;
    }

    // - PRIVATE

	private static final Logger logger = Logger.getLogger(SuperSimpleDBImpl.class);

    private static final int SIMPLEDB_MAX_ATTR_NAME_VALUE_BYTES = 1024;
    private static final String SIMPLEDB_SUPPORTED_ALTERNATE_BASE64_ENCODING = "base64";

    private static final String UNIQUE_LOCK_ATTR = "ssdb_unique_retrieve" ;

    private static final String LONG_FIELDS_ATTR = "ssdb_has_long_fields" ;
    private static final String LONG_FIELDS_DELIM = "." ;

    private static final int LONG_FIELD_MAX_SIZE_HEX_LENGTH = 5;
    private static final int LONG_ENTRY_HEX_INDEX_LENGTH = 3;

    private static final int BATCH_DELETE_LIMIT = 25;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Inject
    @SuppressWarnings({"UnusedDeclaration"})
    private AmazonSimpleDB simpleDB;

    private final Set<String> checkedDomains = Collections.synchronizedSet(new HashSet<String>());

	/**
	 * Singleton access provided via Spring
	 */
	@SuppressWarnings({"UnusedDeclaration"})
    private SuperSimpleDBImpl() { }

    private <T extends NamedMap> List<ReplaceableAttribute> asReplacebleAttributes(T data, boolean longFields) {
        List<ReplaceableAttribute> attrs = new ArrayList<ReplaceableAttribute>();
        StringBuilder encodedLongEntries = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (longFields && isLongEntry(entry)) {
                addLongEntry(encodedLongEntries, entry);
            } else {
                attrs.add(new ReplaceableAttribute(entry.getKey(), entry.getValue(), true));
            }
        }

        if (longFields && encodedLongEntries.length() > 0) {
            List<String> stringList = Utf8StringUtils.utf8byteSizeSplit(encodedLongEntries.toString(), SIMPLEDB_MAX_ATTR_NAME_VALUE_BYTES - 4);

            String val;
            int chunks = stringList.size();
            for(int i=0; i < chunks; i+=2) {
                val = i+1 < chunks ? stringList.get(i+1) : "";
                attrs.add(new ReplaceableAttribute(getHexIndex(i, LONG_ENTRY_HEX_INDEX_LENGTH) + LONG_FIELDS_DELIM + stringList.get(i), val, false));
            }
            attrs.add(new ReplaceableAttribute(LONG_FIELDS_ATTR, Boolean.TRUE.toString(), true));
        }
        return attrs;
    }

    private String getHexIndex(int i, int length) {
        String hexInt = Integer.toHexString(i);
        StringBuilder result = new StringBuilder();
        if (length < hexInt.length()) {
            throw new IllegalArgumentException("Hex integer " + hexInt + " is longer than requested length " + length);
        }
        for(int k = hexInt.length(); k < length; k++) {
            result.append("0");
        }
        result.append(hexInt);
        return result.toString();
    }

    private void addLongEntry(StringBuilder encodedLongEntries, Map.Entry<String, String> entry) {

        encodedLongEntries.append(getHexIndex(entry.getKey().length(), LONG_FIELD_MAX_SIZE_HEX_LENGTH) )
                .append(LONG_FIELDS_DELIM)
                .append(entry.getKey());

        encodedLongEntries.append(getHexIndex(entry.getValue().length(), LONG_FIELD_MAX_SIZE_HEX_LENGTH) )
                .append(LONG_FIELDS_DELIM)
                .append(entry.getValue());
    }

    private boolean isLongEntry(Map.Entry<String, String> entry) {
        return entry.getKey() != null && entry.getKey().getBytes(UTF8).length > SIMPLEDB_MAX_ATTR_NAME_VALUE_BYTES ||
               entry.getValue() != null && entry.getValue().getBytes(UTF8).length > SIMPLEDB_MAX_ATTR_NAME_VALUE_BYTES;
    }

    private Map<String, String> asMap(List<Attribute> attributes) {
        Map<String,String> result = new LinkedHashMap<String, String>();

        for(Attribute a : attributes) {
            result.put(decodeAttributeString(a.getName(), a.getAlternateNameEncoding()), decodeAttributeString(a.getValue(), a.getAlternateValueEncoding()));
        }
        if(result.containsKey(LONG_FIELDS_ATTR)) {
            decodeLongEntries(result);
            result.remove(LONG_FIELDS_ATTR);
        }
        logger.debug("Got attributes from SimpleDB: " + result);
        return result;
    }

    private String decodeAttributeString(String value, String alternateEncoding) {
        if (StringUtils.isBlank(alternateEncoding)) return value;
        if (SIMPLEDB_SUPPORTED_ALTERNATE_BASE64_ENCODING.equalsIgnoreCase(alternateEncoding)) {
            return new String(Base64.decodeBase64(value.getBytes()), UTF8);
        } else {
            throw new IllegalArgumentException("Unsupported encoding received from SimpleDB: " + alternateEncoding);
        }
    }

    private void decodeLongEntries(Map<String, String> result) {

        SortedMap<String,String> longEntries = new TreeMap<String, String>();

        for (String key : result.keySet()) {
            if ( key.length() > LONG_ENTRY_HEX_INDEX_LENGTH + 1 &&
                 LONG_FIELDS_DELIM.equals(key.substring(LONG_ENTRY_HEX_INDEX_LENGTH, LONG_ENTRY_HEX_INDEX_LENGTH + 1))) {
                longEntries.put(key, result.get(key));
            }
        }

        StringBuilder encodedLongEntries = new StringBuilder();
        for (Map.Entry<String, String> longEntryChunk : longEntries.entrySet()) {
            encodedLongEntries.append(longEntryChunk.getKey().substring(LONG_ENTRY_HEX_INDEX_LENGTH + 1)).append(longEntryChunk.getValue());
            result.remove(longEntryChunk.getKey());
        }

        Pair<String,String> nextChunk = new Pair<String, String>(null, encodedLongEntries.toString());

        String key = null;
        String value = null;
        while( StringUtils.isNotEmpty(nextChunk.getRight()) ) {
            nextChunk = extractNextChunk(nextChunk.getRight());
            if (key == null) {
                key = nextChunk.getLeft();
            } else {
                value = nextChunk.getLeft();
            }
            if (value != null) {
                result.put(key, value);
                key = null;
                value = null;
            }
        }
    }

    private Pair<String,String> extractNextChunk(String encodedLongEntries) {
        try {
            if ( StringUtils.isNotEmpty(encodedLongEntries) &&
                 encodedLongEntries.length() > LONG_FIELD_MAX_SIZE_HEX_LENGTH &&
                 LONG_FIELDS_DELIM.equals(encodedLongEntries.substring(LONG_FIELD_MAX_SIZE_HEX_LENGTH, LONG_FIELD_MAX_SIZE_HEX_LENGTH + 1))) {
                int length = Integer.parseInt(encodedLongEntries.substring(0, LONG_FIELD_MAX_SIZE_HEX_LENGTH), 16);
                int remainderBeginIndex = LONG_FIELD_MAX_SIZE_HEX_LENGTH + 1 + length;
                return new Pair<String, String>(
                        encodedLongEntries.substring(LONG_FIELD_MAX_SIZE_HEX_LENGTH + 1, LONG_FIELD_MAX_SIZE_HEX_LENGTH + 1 + length),
                        remainderBeginIndex >= encodedLongEntries.length() ? null : encodedLongEntries.substring(remainderBeginIndex)
                );
            }
        } catch (Exception e) {
            // do nothing, no more chunks
        }
        return new Pair<String, String>(encodedLongEntries, null);
    }

    // split and transform the items into multiple lists with BATCH_DELETE_LIMIT (DeletableItem) elements
    private List<List<DeletableItem>> asLimitedDeletableItemLists(List<Item> items) {
        List<List<DeletableItem>> result = new ArrayList<List<DeletableItem>>();
        List<DeletableItem> current = null;
        for(Item item : items) {
            if (current == null) {
                current = new ArrayList<DeletableItem>();
                result.add(current);
            }
            current.add(new DeletableItem(item.getName(), decodeAttributes(item.getAttributes())));
            if(current.size() >= BATCH_DELETE_LIMIT) {
                //noinspection AssignmentToNull
                current = null;
            }
        }
        return result;
    }

    private List<Attribute> decodeAttributes(List<Attribute> attributes) {
        List<Attribute> result = new ArrayList<Attribute>();
        for (Attribute a : attributes) {
            result.add(new Attribute(decodeAttributeString(a.getName(), a.getAlternateNameEncoding()), decodeAttributeString(a.getValue(), a.getAlternateValueEncoding())));
        }
        return result;
    }

    /**
     * @return a copy of the list without the attribute having uniqueRetrieveAttr name and uniqueRetrieveToken value, if found;
     * or null if the attribute is not found in the list.
     */
    private List<Attribute> removeExpectedToken(List<Attribute> attributes, String uniqueRetrieveAttr, String uniqueRetrieveToken) {
        List<Attribute> result = new ArrayList<Attribute>();
        boolean found = false;
        for(Attribute attr : attributes) {
            if (uniqueRetrieveAttr.equals(attr.getName()) && uniqueRetrieveToken.equals(attr.getValue())) {
                found = true;
            } else {
                result.add(attr);
            }
        }
        return found ? result : null;
    }

    private List<Item> doSelectWhere(String table, String whereClause, boolean fetchAllTokens) throws SimpleDBException {
        try {
            List<Item> result = new ArrayList<Item>();
            String query = "select * from `" + table + "`" + (StringUtils.isBlank(whereClause) ? "" : " where " + whereClause);
            SelectRequest selectRequest = new SelectRequest(query, true);
            SelectResult selectResult;
            String nextToken;
            do {
                selectResult = simpleDB.select(selectRequest);
                nextToken = selectResult.getNextToken();
                result.addAll(selectResult.getItems());
                selectRequest.setNextToken(nextToken);
            } while (nextToken != null && fetchAllTokens);
            return result;
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }



    private void doDelete(String table, String key, String accessLockToken) throws SimpleDBException {
        try {
            DeleteAttributesRequest deleteRequest = accessLockToken != null ?
                new DeleteAttributesRequest(table, key, null, new UpdateCondition().withName(UNIQUE_LOCK_ATTR).withValue(accessLockToken)) :
                new DeleteAttributesRequest(table, key);
            simpleDB.deleteAttributes(deleteRequest);
            logger.info("SimpleDB deleted " + table + "/" + key);
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }

    }

    private <T extends NamedMap> T lockedRetrieve(String table, Class<T> type, String key, String accessLockToken) throws SimpleDBException {
        try {
            GetAttributesRequest req = new GetAttributesRequest(table, key).withConsistentRead(true);
            List<Attribute> attributes = simpleDB.getAttributes(req).getAttributes();
            List<Attribute> expectedAttributes = removeExpectedToken(attributes, UNIQUE_LOCK_ATTR, accessLockToken);
            if(expectedAttributes == null || expectedAttributes.size() <= 1) {
                logger.warn("SimpleDB unique retrieve failed for " + table + "/" + key);
                return null;
            } else {
                logger.debug("SimpleDB got " + expectedAttributes.size() + " expected attributes for unique access token " + accessLockToken);
                T result = type.newInstance();
                result.init(key, asMap(expectedAttributes));
                logger.info("SimpleDB retrieved and deleted " + table + "/" + key);
                return result;
            }
        } catch (InstantiationException e) {
            throw new SimpleDBException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    private String newAccessLockToken() {
        // todo: return time-based key, clean-up stale locks
        return UUID.randomUUID().toString();
    }

    /**
     * Mark an entry as locked. Callers should finally { accessUnlock(.., accessLockToken) }
     *
     * The lock operation is not guaranteed and no exceptions are thrown if lock fails.
     * Subsequent operations that use the returned access lock token will fail if the lock operation failed.
     *
     * @return access lock token to be used with subsequent locked access operations
     */
    private String accessLock(String table, String key) {
        String uniqueToken = newAccessLockToken();
        final ReplaceableAttribute uniqueRetrieve = new ReplaceableAttribute(UNIQUE_LOCK_ATTR, uniqueToken, false);
        final UpdateCondition nonExistCondition = new UpdateCondition().withName(UNIQUE_LOCK_ATTR).withExists(false);
        PutAttributesRequest uniqueTokenRequest = new PutAttributesRequest(
            table,
            key,
            new ArrayList<ReplaceableAttribute>() {{ add(uniqueRetrieve); }},
            nonExistCondition);

        simpleDB.putAttributes(uniqueTokenRequest);
        return uniqueToken;
    }

    /**
     * Remove the access lock token from the specified entry.
     */
    private void accessUnlock(String table, String key, final String lockToken) {
        try {
            List<Attribute> lockAttr = new ArrayList<Attribute>(1) {{
                add(new Attribute(UNIQUE_LOCK_ATTR, lockToken) );
            }};
            UpdateCondition tokenExists = new UpdateCondition().withName(UNIQUE_LOCK_ATTR).withValue(lockToken);
            simpleDB.deleteAttributes(new DeleteAttributesRequest(table, key, lockAttr, tokenExists));
        } catch (Exception e) {
            logger.error("Error removing access lock (" + lockToken + ") for " + table + " / " + key);
        }
    }
}
