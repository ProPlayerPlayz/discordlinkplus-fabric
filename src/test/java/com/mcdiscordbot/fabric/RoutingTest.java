package com.mcdiscordbot.fabric;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoutingTest {
    @Test void ungroupedPlayersNeverBridge() { assertNull(Routing.room(null, false, false, false)); }
    @Test void separateGroupsStaySeparate() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(first.toString(), Routing.room(first, false, false, false));
        assertNotEquals(Routing.room(first, false, false, false), Routing.room(second, false, false, false));
    }
    @Test void privateGroupNeedsExplicitOptIn() {
        UUID group = UUID.randomUUID();
        assertNull(Routing.room(group, true, false, false));
        assertEquals(group.toString(), Routing.room(group, true, false, true));
    }
    @Test void isolatedGroupsNeverBridgeEvenWhenPrivateGroupsAreEnabled() {
        UUID group = UUID.randomUUID();
        assertNull(Routing.room(group, false, true, false));
        assertNull(Routing.room(group, false, true, true));
        assertNull(Routing.room(group, true, true, true));
    }
    @Test void channelUsesExactGroupNameWithoutPrefixOrUuid() {
        assertEquals("aoeu", VoiceEngine.channelName("aoeu"));
        assertEquals("Same name", VoiceEngine.channelName("Same name"));
        assertEquals("Friends 🎮", VoiceEngine.channelName("Friends 🎮"));
    }
    @Test void channelNameRemainsValidForMalformedInput() {
        assertEquals("Voice group", VoiceEngine.channelName("\n\t"));
        assertEquals("a".repeat(100), VoiceEngine.channelName("a".repeat(200)));
    }
    @Test void disabledTemplateDoesNotRequireCredentialsAndEnabledConfigDoes() {
        var config = new BridgeConfig();
        assertDoesNotThrow(config::validate);
        config.enabled = true;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.guildId = "123456789012345678";
        config.groupCategoryId = "234567890123456789";
        config.botTokens = java.util.List.of("local-test-placeholder");
        assertDoesNotThrow(config::validate);
    }
}
