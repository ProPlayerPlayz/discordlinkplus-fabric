package com.mcdiscordbot.fabric;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ChatRelayTest {
    private ChatConfig config() { var config = new ChatConfig(); config.enabled = true; config.channelId = "234567890123456789"; return config; }
    @Test void shutdownNoticeTakesPriorityOverAFullQueueAndLifecycleCanBeDisabled() {
        var config = config(); var relay = new ChatRelay("guild", config);
        for (int i = 0; i < ChatRelay.CAPACITY; i++) relay.minecraft(UUID.randomUUID(), "Player", null, "message " + i);
        assertTrue(relay.lifecycle(false));
        assertEquals("Server stopped.", relay.nextOutgoing().webhookText());
        assertTrue(relay.metrics().contains("dropped=1"));
        config.serverMessages = false;
        assertFalse(relay.lifecycle(true));
        config.serverMessages = true; config.minecraftToDiscord = false;
        assertFalse(relay.lifecycle(false));
    }
    @Test void offlinePlayerSkinChangesUseANewTextureOnTheNextMessage() {
        var relay = new ChatRelay("guild", config());
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:OfflineUser".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String hash : new String[]{"a".repeat(64), "b".repeat(64)}) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String texture = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            relay.minecraft(offline, "OfflineUser", texture, "hello");
            var message = relay.nextOutgoing();
            assertEquals("https://mc-heads.net/avatar/" + hash + "/128", message.avatar());
            assertFalse(message.avatar().contains(offline.toString()));
        }
    }
    @Test void incomingOnlyAcceptsHumansInTheConfiguredGuildAndChannel() {
        var config = config(); var relay = new ChatRelay("guild", config);
        assertFalse(relay.discord("other", config.channelId, false, false, "User", "hello"));
        assertFalse(relay.discord("guild", "other", false, false, "User", "hello"));
        assertFalse(relay.discord("guild", config.channelId, true, false, "Bot", "echo"));
        assertFalse(relay.discord("guild", config.channelId, false, true, "Webhook", "echo"));
        assertNull(relay.nextIncoming());
        assertTrue(relay.discord("guild", config.channelId, false, false, "Friend", "/op Friend"));
        assertEquals("[Discord] Friend: /op Friend", relay.nextIncoming().text());
        assertNull(relay.nextOutgoing());
    }
    @Test void directionsAndEventsCanBeDisabledIndependently() {
        var config = config(); var relay = new ChatRelay("guild", config);
        config.minecraftToDiscord = false;
        relay.minecraft(UUID.randomUUID(), "Player", null, "hello");
        relay.game("multiplayer.player.joined", "Player joined");
        assertNull(relay.nextOutgoing());
        assertTrue(relay.discord("guild", config.channelId, false, false, "Friend", "hello"));
        config.discordToMinecraft = false;
        assertFalse(relay.discord("guild", config.channelId, false, false, "Friend", "hello"));
        config.minecraftToDiscord = true; config.deathMessages = false;
        relay.game("death.attack.generic", "Player died");
        relay.game("commands.message.display.incoming", "private message");
        relay.game(null, "[Discord] echo");
        assertNull(relay.nextOutgoing());
        relay.game("chat.type.advancement.task", "Player advanced");
        assertTrue(relay.nextOutgoing().system());
    }
    @Test void overloadAndDisconnectedBacklogsAreBoundedAndExpire() {
        var now = new AtomicLong(); var config = config(); var relay = new ChatRelay("guild", config, now::get);
        for (int i = 0; i < ChatRelay.CAPACITY + 5; i++) {
            relay.minecraft(UUID.randomUUID(), "Player", null, "message " + i);
            relay.discord("guild", config.channelId, false, false, "Friend", "message " + i);
        }
        assertTrue(relay.metrics().contains("queued=100/100"));
        assertTrue(relay.metrics().contains("dropped=10"));
        assertEquals("message 0", relay.nextOutgoing().webhookText());
        now.set(61_000_000_000L);
        assertNull(relay.nextOutgoing()); assertNull(relay.nextIncoming());
        assertTrue(relay.metrics().contains("queued=0/0"));
    }
    @Test void closeClearsBothDirectionsAndRejectsLateCallbacks() {
        var config = config(); var relay = new ChatRelay("guild", config);
        relay.minecraft(UUID.randomUUID(), "Player", null, "hello");
        relay.discord("guild", config.channelId, false, false, "Friend", "hello");
        relay.close();
        relay.minecraft(UUID.randomUUID(), "Player", null, "late");
        assertFalse(relay.discord("guild", config.channelId, false, false, "Friend", "late"));
        assertNull(relay.nextOutgoing()); assertNull(relay.nextIncoming());
    }
    @Test void stripsControlAndColourInjectionAndTruncatesBothDirections() {
        var config = config(); var relay = new ChatRelay("guild", config);
        relay.discord("guild", config.channelId, false, false, "§kFriend\nFake", "hello\nworld\u0000" + "x".repeat(3000));
        String text = relay.nextIncoming().text();
        assertFalse(text.contains("§")); assertFalse(text.contains("\n")); assertFalse(text.contains("\u0000"));
        assertTrue(text.startsWith("[Discord] Friend Fake: hello world"));
        assertTrue(text.length() < 1000);
        relay.minecraft(UUID.randomUUID(), "_k1R4", null, "x".repeat(4000));
        var message = relay.nextOutgoing();
        assertTrue(message.botText().contains("\\_k1R4"));
        assertTrue(message.webhookText().length() <= 1900);
    }
}
