package com.softinstigate.restheart;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import org.pac4j.undertow.ProfileWrapper;
import org.pac4j.undertow.utils.StorageHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: msuchecki
 * Date: 05.01.15
 * Time: 16:49
 */
public class RolesManager {

    final IdentityManager identityManager;

    public RolesManager(IdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    public Set<String> roles(String id) {
        Account verify = identityManager.verify(id, new PasswordCredential("secret".toCharArray()));

        return verify != null ? verify.getRoles() : new HashSet<>();
    }

}
