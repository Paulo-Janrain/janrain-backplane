package com.janrain.message;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.*;

/**
 * Base Message implementation backed by a NamedMap.
 * Validation of the MessageFields defined in subclasses is enforced from the constructor.
 *
 * @author Johnny Bufu
 */
public abstract class AbstractMessage extends AbstractNamedMap implements Message {

    // - PUBLIC

    @Override
    public void init(String name, Map<String, String> data) {
        super.init(name, data);
        validate();
    }

    @Override
    public void setName(String name) {
        String id = getIdValue();
        if (name == null || ! name.equals(id))
            throw new IllegalArgumentException("Message identifier value different than ID field value: " + name + " : " + id);
    }

    @Override
    public String getName() {
        // NamedMap's name will be the message's ID field value
        return getIdValue();
    }

    public String get(MessageField f) {
        return get(f.getFieldName());
    }

    public void validate() {
        Set<? extends MessageField> allFields = getFields();
        // validate all fields
        for(MessageField f : allFields) {
            f.validate(get(f));
        }
    }

    // todo: rename toJsonString?
    public String toString() {

        ObjectMapper mapper = new ObjectMapper();
        String messageName = getClass().getSimpleName();
        String value;
        try {
            value = messageName + ": " + mapper.writeValueAsString(entrySet());
        }
        catch (Exception e) {
            value = messageName + ": (Unable to convert object to JSON -- " + e.getMessage() + ")";
        }

        return value;
    }

    // - PROTECTED


    protected AbstractMessage() { }

    protected AbstractMessage(Map<String, String> data) {
        super(data);
        validate();
    }

    /**
     * @throws NullPointerException if the value is null.
     */
    protected static void validateNotNull(String fieldName, String value) {
        if (value == null) throw new NullPointerException("Field " + fieldName + " cannot be null.");
    }

    /**
     * @throws IllegalArgumentException if the value is not a valid integer.
     */
    protected static void validateInt(String fieldName, String value) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Number expected for " + fieldName + " got: " + value, e);
        }
    }

    protected static Map<String, String> toStringMap(Map<String, Object> data) {
        Map<String,String> stringData = new LinkedHashMap<String, String>();
        for (Entry<String, Object> entry : data.entrySet()) {
            stringData.put(entry.getKey(), entry.getValue().toString());
        }
        return stringData;
    }

}
