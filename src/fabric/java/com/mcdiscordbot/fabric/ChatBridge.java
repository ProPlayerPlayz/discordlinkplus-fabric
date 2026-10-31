package com.mcdiscordbot.fabric;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Reuses the first voice-pool bot; every Discord REST operation runs on this worker. */
final class ChatBridge extends ListenerAdapter implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("DiscordLinkPlusChat");
    private final BridgeConfig config;
    private final ChatRelay relay;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DiscordLinkPlus-chat"); t.setDaemon(true); return t;
    });
    private volatile JDA client;
    private volatile boolean closed;
    private volatile String state = "waiting for first bot";
    private Webhook webhook;
    private boolean checkedWebhook;
    private long lastWarning;

    ChatBridge(BridgeConfig config) { this.config = config; relay = new ChatRelay(config.guildId, config.chat); }
    void start() { if (config.chat.enabled) worker.scheduleWithFixedDelay(this::work, 100, 100, TimeUnit.MILLISECONDS); }
    void bind(JDA firstBot) { if (!closed) client = firstBot; }
    ChatRelay relay() { return relay; }
    String status() { return config.chat.enabled ? "Text chat: " + state + "; " + relay.metrics() : "Text chat disabled."; }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (closed || !config.chat.enabled || !config.chat.discordToMinecraft || !event.isFromGuild()) return;
        if (!config.guildId.equals(event.getGuild().getId()) || !config.chat.channelId.equals(event.getChannel().getId())) return;
        if (event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) return;
        String name = event.getMember() == null ? event.getAuthor().getName() : event.getMember().getEffectiveName();
        StringBuilder text = new StringBuilder(event.getMessage().getContentDisplay());
        event.getMessage().getAttachments().stream().limit(5).forEach(attachment -> text.append(" [Attachment] ").append(attachment.getUrl()));
        relay.discord(event.getGuild().getId(), event.getChannel().getId(), false, false, name, text.toString());
    }

    void work() {
        if (closed) return;
        try {
            JDA current = client;
            if (current == null || current.getStatus() != JDA.Status.CONNECTED) { state = "waiting for first bot (check Message Content Intent if chat was enabled)"; return; }
            var guild = current.getGuildById(config.guildId);
            TextChannel channel = guild == null ? null : guild.getTextChannelById(config.chat.channelId);
            if (channel == null) { state = "configured text channel missing or inaccessible"; return; }
            if (config.chat.useWebhooks && config.chat.minecraftToDiscord && !checkedWebhook) setupWebhook(channel, current);
            state = config.chat.useWebhooks && config.chat.minecraftToDiscord ? (webhook == null ? "connected (bot-message fallback; check Manage Webhooks and reload)" : "connected (player webhooks)") : "connected (bot messages)";
            ChatRelay.Outgoing message = relay.nextOutgoing();
            if (message == null) return;
            try {
                if (!message.system() && webhook != null) {
                    await(webhook.sendMessage(message.webhookText()).setUsername(message.player()).setAvatarUrl(message.avatar())
                        .setAllowedMentions(List.of()).setSuppressEmbeds(true));
                } else {
                    await(channel.sendMessage(message.botText()).setAllowedMentions(List.of()).setSuppressEmbeds(true));
                }
                relay.sent();
            } catch (Exception e) {
                // A timed-out request may already have been delivered. Do not retry the same message and duplicate it.
                relay.failed();
                if (!message.system() && webhook != null) webhook = null;
                warn(e);
            }
        } catch (Exception e) { relay.failed(); warn(e); }
    }

    private void setupWebhook(TextChannel channel, JDA current) throws InterruptedException {
        checkedWebhook = true;
        try {
            webhook = await(channel.retrieveWebhooks()).stream()
                .filter(w -> w.getType() == WebhookType.INCOMING && "DiscordLink+ Chat".equals(w.getName())
                    && w.getOwnerAsUser() != null && current.getSelfUser().getId().equals(w.getOwnerAsUser().getId()))
                .findFirst().orElse(null);
            if (webhook == null) webhook = await(channel.createWebhook("DiscordLink+ Chat"));
        } catch (InterruptedException e) { throw e; }
        catch (Exception e) { webhook = null; warn(e); }
    }

    private static <T> T await(RestAction<T> action) throws Exception {
        var future = action.submit();
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (Exception e) { future.cancel(false); throw e; }
    }

    private void warn(Exception error) {
        if (error instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
        long now = System.nanoTime();
        if (now - lastWarning < 30_000_000_000L && lastWarning != 0) return;
        lastWarning = now;
        LOG.warn("Text relay request failed ({}). Check /discordlink status, channel permissions and config. Message content and credentials are not logged.", error.getClass().getSimpleName());
    }

    @Override public void close() { closed = true; relay.close(); worker.shutdownNow(); client = null; }
}
