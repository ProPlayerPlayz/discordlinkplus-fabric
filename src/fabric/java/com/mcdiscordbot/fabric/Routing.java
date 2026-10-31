package com.mcdiscordbot.fabric;

import java.util.UUID;

public final class Routing {
    private Routing() {}

    public static String room(UUID groupId, boolean passwordProtected, boolean isolated, boolean bridgePasswordGroups) {
        if (groupId == null || isolated) return null;
        if (passwordProtected && !bridgePasswordGroups) return null;
        return groupId.toString();
    }
}
