package com.softinstigate.restheart;

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
        return userProfile;
    }
}
