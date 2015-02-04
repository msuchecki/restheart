package com.softinstigate.restheart.security.impl;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by wkaminski on 30.01.15.
 */
public class SimpleCollectionIdentityManager extends FileConfigurable implements IdentityManager {


    public static final Logger logger = LoggerFactory.getLogger(SimpleCollectionIdentityManager.class);

    public SimpleCollectionIdentityManager(Map<String, Object> arguments) {
        loadConfig(arguments);
    }

    String dbName;
    String usersCollection;
    String userIdProp;
    String rolesProp;

    @Override
    public Account verify(Account account) {
        return account;
    }

    @Override
    public Account verify(String s, Credential credential) {
        DBCollection col = CollectionDAO.getCollection(dbName, usersCollection);
        DBObject q = BasicDBObjectBuilder.start().add(userIdProp, s).get();
        DBObject accData = col.findOne(q);
        if (accData != null) {

            List<String> rolesList = (List<String>) accData.get(rolesProp);
            return new SimpleAccount(s, "secret".toCharArray(), rolesList == null ? new HashSet<>() : new HashSet(rolesList));

        }
        return null;
    }

    @Override
    public Account verify(Credential credential) {
        return null;
    }

    @Override
    protected void init(Map<String, Object> conf) {
        Object _colCfg = conf.get("collectionusers");

        if (_colCfg == null || !(_colCfg instanceof Map)) {
            logger.error("wrong configuration file format.");
            throw new IllegalArgumentException("wrong configuration file format. missing mandatory users section");
        }

        Map<String, String> colCfg = (Map<String, String>) _colCfg;
        dbName = colCfg.getOrDefault("dbname", "test");
        usersCollection = colCfg.getOrDefault("collection", "acl");
        userIdProp = colCfg.getOrDefault("useridprop", "user_id");
        rolesProp = colCfg.getOrDefault("rolesprop", "roles");
    }
}
