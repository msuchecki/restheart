/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.softinstigate.restheart.security.impl;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import java.security.Principal;
import java.util.Set;

/**
 *
 * @author Andrea Di Cesare
 */
public class SimpleAccount implements Account {

    private Principal principal;
    private PasswordCredential credential;
    private Set<String> roles;

    /**
     *
     * @param name
     * @param password
     * @param roles
     */
    public SimpleAccount(String name, char[] password, Set<String> roles) {
        if (name == null) {
            throw new IllegalArgumentException("argument principal cannot be null");
        }

        if (password == null) {
            throw new IllegalArgumentException("argument password cannot be null");
        }

        if (roles == null) {
            throw new IllegalArgumentException("argument roles cannot be null nor empty");
        }

        this.principal = new SimplePrincipal(name);
        this.credential = new PasswordCredential(password);
        this.roles = roles;
    }

    /**
     *
     * @return
     */
    @Override
    public Principal getPrincipal() {
        return principal;
    }

    /**
     *
     * @return
     */
    public PasswordCredential getCredentials() {
        return credential;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }
}
