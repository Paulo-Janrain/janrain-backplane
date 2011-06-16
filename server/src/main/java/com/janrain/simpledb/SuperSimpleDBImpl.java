package com.janrain.simpledb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.*;
import com.janrain.message.NamedMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
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
@Service(value="superSimpleDB")
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
        try {
            checkDomain(table);
            type.cast(data); // enforce runtime type-safety
            simpleDB.putAttributes(new PutAttributesRequest(table, data.getName(), asReplacebleAttributes(data)));
            logger.info("SimpleDB stored " + table + "/" + data.getName());
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public void delete(String table, String key) throws SimpleDBException {

        doDelete(table, key, null);
    }

    @Override
    public void deleteWhere(String table, String whereClause) throws SimpleDBException {
        try {
            List<Item> items = doSelectWhere(table, whereClause);
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
    public <T extends NamedMap> List<T> retrieve(String table, Class<T> type) throws SimpleDBException {
        return retrieveWhere(table, type, null);
    }

    @Override
    public <T extends NamedMap> List<T> retrieveWhere(String table, Class<T> type, String whereClause) throws SimpleDBException {
        try {
            List<T> result = new ArrayList<T>();
            for (Item item : doSelectWhere(table, whereClause)) {
                T resultItem = type.newInstance();
                resultItem.init(item.getName(), asMap(item.getAttributes()));
                result.add(resultItem);
            }
            logger.info("SimpleDB retrieved from " + table + (whereClause != null ? " for query `" + whereClause + "` ": " ")  + result.size() + " entries");
            return result;
        } catch (InstantiationException e) {
            throw new SimpleDBException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    @Override
    public <T extends NamedMap> T retrieveAndDelete(String table, Class<T> type, String key) throws SimpleDBException {
        try {
            String uniqueRetrieveToken = UUID.randomUUID().toString();
            final ReplaceableAttribute uniqueRetrieve = new ReplaceableAttribute(UNIQUE_RETRIEVE_ATTR, uniqueRetrieveToken, false);
            final UpdateCondition nonExistCondition = new UpdateCondition().withName(UNIQUE_RETRIEVE_ATTR).withExists(false);
            PutAttributesRequest uniqueTokenRequest = new PutAttributesRequest(
                table,
                key,
                new ArrayList<ReplaceableAttribute>() {{ add(uniqueRetrieve); }},
                nonExistCondition);

            simpleDB.putAttributes(uniqueTokenRequest);

            GetAttributesRequest req = new GetAttributesRequest(table, key).withConsistentRead(true);
            List<Attribute> attributes = simpleDB.getAttributes(req).getAttributes();
            List<Attribute> expectedAttributes = removeExpectedToken(attributes, UNIQUE_RETRIEVE_ATTR, uniqueRetrieveToken);
            logger.debug("SimpleDB got " + expectedAttributes.size() + " expected attributes for unique retrieve token " + uniqueRetrieveToken);
            if(expectedAttributes == null || expectedAttributes.size() <= 1) {
                logger.warn("SimpleDB unique retrieve failed for " + table + "/" + key);
                return null;
            } else {
                UpdateCondition tokenExists = new UpdateCondition().withName(UNIQUE_RETRIEVE_ATTR).withValue(uniqueRetrieveToken);
                doDelete(table, key, tokenExists);
                T result = type.newInstance();
                result.init(key, asMap(expectedAttributes));
                logger.info("SimpleDB retrieved and deleted " + table + "/" + key);
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
    public void drop(String table) throws SimpleDBException {
        try {
            simpleDB.deleteDomain(new DeleteDomainRequest(table));
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    // - PACKAGE

    public SuperSimpleDBImpl(AmazonSimpleDB simpleDB) {
        this.simpleDB = simpleDB;
    }

    // - PRIVATE

	private static final Logger logger = Logger.getLogger(SuperSimpleDBImpl.class);

    private static final String UNIQUE_RETRIEVE_ATTR = "ssdb_unique_retrieve" ;

    private static final int BATCH_DELETE_LIMIT = 25;

    @Inject
    @SuppressWarnings({"UnusedDeclaration"})
    private AmazonSimpleDB simpleDB;

    private final Set<String> checkedDomains = Collections.synchronizedSet(new HashSet<String>());

	/**
	 * Singleton access provided via Spring
	 */
	private SuperSimpleDBImpl() { }

    /**
     * Check if the domain (table) exists, and initialize it only if doesn't (since creating it is potentially expensive)
     *
     * @param table
     */
    private void checkDomain(String table) {
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

        simpleDB.createDomain(new CreateDomainRequest(table));
        checkedDomains.add(table);
    }

    private <T extends NamedMap> List<ReplaceableAttribute> asReplacebleAttributes(T data) {
        List<ReplaceableAttribute> attrs = new ArrayList<ReplaceableAttribute>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            attrs.add(new ReplaceableAttribute(entry.getKey(), entry.getValue(), true));
        }
        return attrs;
    }

    private Map<String, String> asMap(List<Attribute> attributes) {
        // todo: deal with encoding from Attribute?
        Map<String,String> result = new LinkedHashMap<String, String>();
        for(Attribute a : attributes) {
            result.put(a.getName(), a.getValue());
        }
        logger.debug("Got attributes from SimpleDB: " + result);
        return result;
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
            current.add(new DeletableItem(item.getName(), item.getAttributes()));
            if(current.size() >= BATCH_DELETE_LIMIT) {
                //noinspection AssignmentToNull
                current = null;
            }
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

    private List<Item> doSelectWhere(String table, String whereClause) throws SimpleDBException {
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
            } while (nextToken != null);
            return result;
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }
    }

    private void doDelete(String table, String key, UpdateCondition condition) throws SimpleDBException {
        try {
            DeleteAttributesRequest deleteRequest = condition != null ?
                new DeleteAttributesRequest(table, key, null, condition) :
                new DeleteAttributesRequest(table, key);
            simpleDB.deleteAttributes(deleteRequest);
            logger.info("SimpleDB deleted " + table + "/" + key);
        } catch (AmazonClientException e) {
            throw new SimpleDBException(e.getMessage(), e);
        }

    }
}
