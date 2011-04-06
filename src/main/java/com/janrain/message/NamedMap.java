package com.janrain.message;

import java.util.Map;

/**
 * A Map of String keys and values with an associated name. Can be saved easily into Amazon's SimpleDB.
 *
 * Concrete implementations must provide a nullary constructor, to be used before the pseudo-deserialization from a Map instance.
 * @see NamedMap#init(String, java.util.Map)
 *
 * @author Johnny Bufu
 */
public interface NamedMap extends Map<String,String> {

    /**
     * @return the name (identifier) for the map
     */
    String getName();

    /**
     * Initializes the named map from a Map instance and a name.
     *
     * Concrete implementations must provide a nullary constructor, for the pseudo-deserialization from a Map instance.
     *
     * @param name the named map's name
     * @param data the named map's data
     */
    void init(String name, Map<String,String> data);
}
