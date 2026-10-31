package com.mcdiscordbot.fabric;

/** Optional text relay; old voice-only configs keep working unchanged. */
public final class ChatConfig {
    public boolean enabled = false;
    public String channelId = "";
    public boolean minecraftToDiscord = true;
    public boolean discordToMinecraft = true;
    public boolean useWebhooks = true;
    public String avatarUrl = "https://mc-heads.net/avatar/%texture%/128";
    public String minecraftToDiscordFormat = "**%player%**: %message%";
    public String webhookMessageFormat = "%message%";
    public String discordToMinecraftFormat = "[Discord] %user%: %message%";
    public boolean joinLeaveMessages = true;
    public boolean deathMessages = true;
    public boolean advancementMessages = true;
    public String serverName = "Minecraft Server";
    public boolean serverMessages = true;
    public String serverStartedMessage = "Server started.";
    public String serverStoppedMessage = "Server stopped.";

    public void validate() {
        if (!enabled) return;
        BridgeConfig.requireId(channelId, "chat.channelId");
        if (!ChatBridge.validWebhookName(serverName)) throw new IllegalArgumentException("Invalid chat.serverName for a Discord webhook");
        for (String format : new String[]{minecraftToDiscordFormat, webhookMessageFormat, discordToMinecraftFormat, serverStartedMessage, serverStoppedMessage}) {
            if (format == null || format.isBlank() || format.length() > 1000) throw new IllegalArgumentException("Invalid chat format");
        }
        if (avatarUrl == null || avatarUrl.length() > 1000) throw new IllegalArgumentException("Invalid chat.avatarUrl");
        if (!avatarUrl.isBlank()) {
            var uri = java.net.URI.create(avatarUrl.replace("%player%", "Player").replace("%uuid%", "00000000-0000-0000-0000-000000000000").replace("%texture%", "0000"));
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                throw new IllegalArgumentException("chat.avatarUrl must be HTTP(S) or empty");
        }
    }
}
