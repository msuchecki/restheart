package com.softinstigate.restheart;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.security.AccessManager;
import com.softinstigate.restheart.security.impl.FileConfigurable;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import org.pac4j.undertow.ProfileWrapper;
import org.pac4j.undertow.utils.StorageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: msuchecki
 * Date: 10.01.15
 * Time: 22:10
 */
public class DocumentAccessManager extends FileConfigurable implements AccessManager {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAccessManager.class);


    public DocumentAccessManager(Map<String, Object> arguments) {
        loadConfig(arguments);
    }

    public DocumentAccessManager() {

    }

    @Override
    public boolean isAllowed(HttpServerExchange exchange, RequestContext context) {

        ProfileWrapper profile = StorageHelper.getProfile(exchange);

        if (context == null || profile.getAccount().getRoles().contains(Roles.ADMIN_ROLE)) {
            return true;
        }


        BasicDBObject query;
        query = new BasicDBObject("doc_id", context.getDocumentId());
        query.append("user_id", profile.getProfile().getEmail());
        query.append("collection", context.getCollectionName());

        DBObject document = CollectionDAO.getCollection(context.getDBName(), "acl").findOne(query);
        logger.debug("document found {}", document);
        return document != null;
    }

    @Override
    public HashMap<String, Set<Predicate>> getAcl() {
        return null;
    }

    @Override
    protected void init(Map<String, Object> conf) {
        logger.debug("config:{}", conf);
    }
}
