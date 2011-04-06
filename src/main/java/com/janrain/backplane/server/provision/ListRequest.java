package com.janrain.backplane.server.provision;

import java.util.Collections;
import java.util.List;

/**
 * @author Johnny Bufu
 */
public class ListRequest {

    // - PUBLIC

    public String getAdmin() {
        return admin;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setAdmin(String admin) {
        this.admin = admin;
    }

    public String getSecret() {
        return secret;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getEntities() {
        return entities;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setEntities(List<String> entities) {
        this.entities = Collections.unmodifiableList(entities);
    }

    // - PRIVATE

    private String admin;
    private String secret;

    private List<String> entities;
}
