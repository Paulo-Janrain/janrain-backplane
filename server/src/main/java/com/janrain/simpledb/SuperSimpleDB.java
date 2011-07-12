package com.janrain.simpledb;

import com.janrain.message.NamedMap;

import java.util.List;

/**
 * A simplified interface to Amazon's SimpleDB store for persisting NamedMap data structures.
 *
 * SimpleDB domains (similar to database tables) are created transparently when needed,
 * since they don't need a schema.
 *
 * The NamedMap's name is used as the SimpleDB's item name (the items key in the database).
 * Amazon's AmazonClientException runtime exceptions are wrapped into our own SimpleDBException
 *
 * @see com.janrain.message.AbstractNamedMap
 * @author Johnny Bufu
 */
public interface SuperSimpleDB {

    /**
     * Creates a new table/domain with the provided name.
     */
    public void create(String table) throws SimpleDBException;

    /**
     * Stores the provided entry under the specified table/domain (which is created if it doesn't exist).
     */
    public <T extends NamedMap> void store(String table, Class<T> type, T data) throws SimpleDBException;

    /**
     * Updates an expected, existing entry with a new one.
     * Throws if the provided expected entry is not equal to the one in the database.
     */
    public <T extends NamedMap> void update(String table, Class<T> type, T expected, T updated) throws SimpleDBException;

    /**
     * Deletes the entry associated with the provided key from the specified table.
     */
    public void delete(String table, String key) throws SimpleDBException;

    /**
     * Deletes all entries from the specified table matching the provided where clause.
     */
    public void deleteWhere(String table, String whereClause) throws SimpleDBException;

    /**
     * Returns the entry for the provided key from the specified table/domain, or null if not found.
     */
    public <T extends NamedMap> T retrieve(String table, Class<T> type, String key) throws SimpleDBException;

    /**
     * Retrieves all entries from the specified table/domain.
     */
    public <T extends NamedMap> List<T> retrieve(String table, Class<T> type) throws SimpleDBException;

    /**
     * Retrieves all entries from the specified table/domain matching the provided where clause.
     * @param whereClause can be null or empty, in which case all entries are returned
     */
    public <T extends NamedMap> List<T> retrieveWhere(String table, Class<T> type, String whereClause) throws SimpleDBException;

    /**
     * Retrieves and deletes atomically the entry for the provided key from the specified table/domain.
     * The returned entry is guaranteed to not be returned for any other (concurrent) retrieveAndDelete calls for the same key.
     */
    public <T extends NamedMap> T retrieveAndDelete(String table, Class<T> type, String key) throws SimpleDBException;

    /**
     * Deletes the specified table/domain from SimpleDB.
     */
    void drop(String table) throws SimpleDBException;
}
