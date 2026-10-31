package com.mcdiscordbot.fabric;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoutingTest {
    @Test void ungroupedPlayersNeverBridge() { assertNull(Routing.room(null, false, false)); }
    @Test void separateGroupsStaySeparate() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(first.toString(), Routing.room(first, false, false));
        assertNotEquals(Routing.room(first, false, false), Routing.room(second, false, false));
    }
    @Test void privateGroupNeedsExplicitOptIn() {
        UUID group = UUID.randomUUID();
        assertNull(Routing.room(group, true, false));
        assertEquals(group.toString(), Routing.room(group, true, true));
    }
    @Test void distinctUuidSuffixAvoidsSameNameGroupCollision() {
        String a = VoiceEngine.channelName("MC · ", "Same name", "12345678-0000-0000-0000-000000000000");
        String b = VoiceEngine.channelName("MC · ", "Same name", "87654321-0000-0000-0000-000000000000");
        assertNotEquals(a, b);
        assertTrue(VoiceEngine.channelName("MC · ", "a".repeat(200), UUID.randomUUID().toString()).length() <= 100);
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
