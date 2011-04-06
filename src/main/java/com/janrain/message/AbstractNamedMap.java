package com.janrain.message;

import java.util.*;

/**
 * Base implementation for the NamedMap interface, delegates to a Map provided as a constructor parameter.
 *
 * @author Johnny Bufu
 */
public abstract class AbstractNamedMap implements NamedMap {

    // - PUBLIC
    public AbstractNamedMap(Map<String,String> data) {
        map.putAll(data);
    }

    public abstract void setName(String name);

    @Override
    public void init(String name, Map<String, String> data) {
        map.clear();
        map.putAll(data);
        setName(name);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return map.get(key);
    }

    @Override
    public String put(String key, String value) {
        return map.put(key, value);
    }

    @Override
    public String remove(Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<String> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractNamedMap that = (AbstractNamedMap) o;

        if (map != null ? !map.equals(that.map) : that.map != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return map != null ? map.hashCode() : 0;
    }

    // - PROTECTED

    protected AbstractNamedMap() { }


    // - PRIVATE

    private final Map<String,String> map = new LinkedHashMap<String, String>();
}
