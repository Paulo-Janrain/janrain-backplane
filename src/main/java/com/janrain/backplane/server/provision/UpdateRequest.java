package com.janrain.backplane.server.provision;

import com.janrain.message.AbstractMessage;

import java.util.Collections;
import java.util.List;

/**
 * @author Johnny Bufu
 */
public class UpdateRequest<T extends AbstractMessage> {

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

    public List<T> getConfigs() {
        return configs;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setConfigs(List<T> configs) {
        this.configs = Collections.unmodifiableList(configs);
    }
    
    // - PRIVATE

    private String admin;
    private String secret;

    private List<T> configs;
}
