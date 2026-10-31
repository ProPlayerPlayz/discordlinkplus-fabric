package com.mcdiscordbot.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BridgeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = false;
    public String guildId = "";
    public String groupCategoryId = "";
    public boolean bridgePasswordGroups = false;
    public boolean deleteEmptyGroupChannels = true;
    public List<String> botTokens = new ArrayList<>(List.of("PASTE_BOT_TOKEN_HERE"));
    public ChatConfig chat = new ChatConfig();

    public static BridgeConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new BridgeConfig()) + "\n");
        }
        try (var reader = Files.newBufferedReader(path)) {
            BridgeConfig result = GSON.fromJson(reader, BridgeConfig.class);
            if (result == null) throw new IOException("Configuration is empty");
            result.validate();
            return result;
        } catch (RuntimeException e) { throw new IOException("Invalid voice configuration (check JSON and IDs)", e); }
    }

    public List<String> tokens() {
        if (botTokens == null) return List.of();
        return botTokens.stream().filter(t -> t != null && !t.isBlank() && !t.contains("PASTE_")).map(String::trim).distinct().toList();
    }

    public void validate() {
        if (chat == null) chat = new ChatConfig();
        chat.validate();
        if (!enabled && !chat.enabled) return;
        requireId(guildId, "guildId");
        if (enabled) requireId(groupCategoryId, "groupCategoryId");
        if (tokens().isEmpty()) throw new IllegalArgumentException("Add at least one bot token");
    }

    static void requireId(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{15,19}")) throw new IllegalArgumentException("Invalid " + field);
        Long.parseUnsignedLong(value);
    }
}
