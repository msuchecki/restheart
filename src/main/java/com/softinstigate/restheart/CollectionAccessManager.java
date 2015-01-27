package com.softinstigate.restheart;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.security.AccessManager;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import org.pac4j.undertow.ProfileWrapper;
import org.pac4j.undertow.utils.StorageHelper;

import java.util.HashMap;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: msuchecki
 * Date: 10.01.15
 * Time: 22:10
 */
public class CollectionAccessManager {

    public boolean isAllowed(HttpServerExchange exchange, RequestContext context, DBObject dbObject) {

        ProfileWrapper profile = StorageHelper.getProfile(exchange);

        if (profile.getAccount().getRoles().contains(Roles.ADMIN_ROLE)) {
            return true;
        }

        BasicDBObject query;
        query = new BasicDBObject("doc_id", dbObject.get("_id"));
        query.append("user_id", profile.getProfile().getEmail());
        query.append("collection", context.getCollectionName());

        DBObject document = CollectionDAO.getCollection(context.getDBName(), "acl").findOne(query);

        return document != null;
    }
}
