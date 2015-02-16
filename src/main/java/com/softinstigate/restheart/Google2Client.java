package com.softinstigate.restheart;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import org.pac4j.oauth.profile.google2.Google2Profile;

/**
 * Created with IntelliJ IDEA.
 * User: msuchecki
 * Date: 05.01.15
 * Time: 16:48
 */
public class Google2Client extends org.pac4j.oauth.client.Google2Client {

    final RolesManager rolesManager;

    public Google2Client(String key, String secret, RolesManager rolesManager) {
        super(key, secret);
        this.rolesManager = rolesManager;
    }

    @Override
    protected Google2Profile extractUserProfile(String body) {
        Google2Profile userProfile = super.extractUserProfile(body);

        for (String role : rolesManager.roles(userProfile.getEmail())) {
            userProfile.addRole(role);
        }


        DBCollection col = CollectionDAO.getCollection("naszadm", "accounts");
        DBObject q = BasicDBObjectBuilder.start().add("email", userProfile.getEmail()).get();
        DBObject accData = col.findOne(q);
        if (accData != null) {

//            userProfile.addAttribute("_id", accData.get("_id"));
            userProfile.setId(accData.get("_id"));


            DBObject u = BasicDBObjectBuilder.start()
                    .add("name", userProfile.getDisplayName())
                    .add("avatar", userProfile.getPictureUrl())
                    .get();

            accData.put("avatar", userProfile.getPictureUrl());
            accData.put("name", userProfile.getDisplayName());

            col.update(q, accData, true, false);

        }

        return userProfile;
    }
}
