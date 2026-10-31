package com.mcdiscordbot.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChatTextTest {
    @Test void templatesDoNotReinterpretPlaceholdersInsideUserContent() {
        String content = "%user% $1 " + (char) 92;
        assertEquals("Name: " + content, ChatText.render("%user%: %message%", Map.of("user", "Name", "message", content)));
    }
    @Test void truncationPreservesUnicodePairs() {
        assertEquals("hello…", ChatText.limit("hello😀hello", 7));
        assertEquals("😀", ChatText.limit("😀", 2));
    }
    @Test void resolvesTextureHashForOfflineCustomSkinsAndFallsBackOnMalformedProfiles() {
        UUID id = UUID.randomUUID(); String hash = "a".repeat(64);
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        String property = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("https://mc-heads.net/avatar/" + hash + "/128", ChatText.avatar(new ChatConfig().avatarUrl, id, "_k1R4", property));
        assertEquals("https://mc-heads.net/avatar/" + id + "/128", ChatText.avatar(new ChatConfig().avatarUrl, id, "_k1R4", "bad"));
        assertNull(ChatText.avatar("", id, "Player", null));
    }
    @Test void oldVoiceConfigDefaultsToDisabledChatAndChatOnlyDoesNotRequireACategory(@TempDir Path temp) throws Exception {
        var path = temp.resolve("config.json");
        Files.writeString(path, "{\"enabled\":false}");
        assertFalse(BridgeConfig.load(path).chat.enabled);
        var config = new BridgeConfig(); config.chat.enabled = true;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.chat.channelId = "234567890123456789";
        config.guildId = "123456789012345678"; config.botTokens = java.util.List.of("test-placeholder");
        assertDoesNotThrow(config::validate);
        config.enabled = true;
        assertThrows(IllegalArgumentException.class, config::validate);
    }
    @Test void invalidEnabledChatConfigurationFailsBeforeReplacingTheRunningBridge() {
        var config = new ChatConfig(); config.enabled = true; config.channelId = "234567890123456789";
        config.avatarUrl = "file:///local";
        assertThrows(IllegalArgumentException.class, config::validate);
        config.avatarUrl = ""; config.discordToMinecraftFormat = null;
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
