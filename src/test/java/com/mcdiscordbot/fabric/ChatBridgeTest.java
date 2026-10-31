package com.mcdiscordbot.fabric;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real JDA event dispatch and send builders with in-memory interfaces, never Discord. */
class ChatBridgeTest {
    private static final String GUILD = "123456789012345678", CHANNEL = "234567890123456789";
    private BridgeConfig config() {
        var config = new BridgeConfig(); config.guildId = GUILD; config.chat.enabled = true; config.chat.channelId = CHANNEL;
        return config;
    }

    @Test void webhookUsesPlayerIdentityAndNoMentionsAndSystemEventsUseTheBot() {
        var config = config(); var network = new Network(false);
        try (var bridge = new ChatBridge(config)) {
            bridge.bind(network.client);
            bridge.relay().minecraft(UUID.randomUUID(), "_k1R4", null, "hello @everyone");
            bridge.work();
            assertEquals(List.of("hello @everyone"), network.webhookMessages);
            assertEquals("_k1R4", network.username);
            assertTrue(network.avatar.startsWith("https://mc-heads.net/avatar/"));
            assertEquals(List.of(), network.mentions);
            bridge.relay().game("multiplayer.player.joined", "Friend joined");
            bridge.work();
            assertEquals(List.of("Friend joined"), network.botMessages);
            assertEquals(List.of(), network.mentions);
        }
    }

    @Test void missingWebhookPermissionFallsBackToBotWithoutDroppingTheChat() {
        var network = new Network(true);
        try (var bridge = new ChatBridge(config())) {
            bridge.bind(network.client);
            bridge.relay().minecraft(UUID.randomUUID(), "Player", null, "hello");
            bridge.work();
            assertEquals(List.of("**Player**: hello"), network.botMessages);
            assertTrue(network.webhookMessages.isEmpty());
            assertTrue(bridge.status().contains("fallback"));
        }
    }

    @Test void realJdaMessageEventIgnoresBotsAndWebhooksAndRelaysHumanTextLiterally() {
        var network = new Network(false);
        try (var bridge = new ChatBridge(config())) {
            bridge.onMessageReceived(network.event(true, false, "echo"));
            bridge.onMessageReceived(network.event(false, true, "echo"));
            assertNull(bridge.relay().nextIncoming());
            bridge.onMessageReceived(network.event(false, false, "/op Player"));
            assertEquals("[Discord] Friend: /op Player", bridge.relay().nextIncoming().text());
            assertNull(bridge.relay().nextOutgoing());
        }
    }

    private static final class Network {
        final List<String> botMessages = new ArrayList<>(), webhookMessages = new ArrayList<>();
        Object mentions;
        String username, avatar;
        final SelfUser self = stub(SelfUser.class, (name, args) -> name.equals("getId") ? "bot" : null);
        final Webhook webhook;
        final TextChannel channel;
        Guild guild;
        final JDA client;

        Network(boolean failWebhookSetup) {
            webhook = stub(Webhook.class, (name, args) -> switch (name) {
                case "getType" -> WebhookType.INCOMING;
                case "getName" -> "DiscordLink+ Chat";
                case "getOwnerAsUser" -> self;
                case "sendMessage" -> { webhookMessages.add(args[0].toString()); yield sendAction(WebhookMessageCreateAction.class); }
                default -> null;
            });
            channel = stub(TextChannel.class, (name, args) -> switch (name) {
                case "getId" -> CHANNEL;
                case "getType" -> ChannelType.TEXT;
                case "getGuild" -> this.guild;
                case "retrieveWebhooks" -> {
                    if (failWebhookSetup) throw new IllegalStateException("permission denied");
                    yield stub(RestAction.class, (method, ignored) -> method.equals("submit") ? CompletableFuture.completedFuture(List.of(webhook)) : null);
                }
                case "sendMessage" -> { botMessages.add(args[0].toString()); yield sendAction(MessageCreateAction.class); }
                default -> null;
            });
            guild = stub(Guild.class, (name, args) -> switch (name) {
                case "getId" -> GUILD;
                case "getTextChannelById" -> channel;
                default -> null;
            });
            client = stub(JDA.class, (name, args) -> switch (name) {
                case "getStatus" -> JDA.Status.CONNECTED;
                case "getGuildById" -> guild;
                case "getSelfUser" -> self;
                default -> null;
            });
        }

        private Object sendAction(Class<?> type) {
            return stub(type, (name, args) -> switch (name) {
                case "setAllowedMentions" -> { mentions = args[0]; yield null; }
                case "setUsername" -> { username = args[0].toString(); yield null; }
                case "setAvatarUrl" -> { avatar = args[0].toString(); yield null; }
                case "submit" -> CompletableFuture.completedFuture(null);
                default -> null;
            });
        }

        MessageReceivedEvent event(boolean bot, boolean viaWebhook, String content) {
            User author = stub(User.class, (name, args) -> switch (name) { case "isBot" -> bot; case "getName" -> "Friend"; default -> null; });
            Member member = stub(Member.class, (name, args) -> name.equals("getEffectiveName") ? "Friend" : null);
            Message message = stub(Message.class, (name, args) -> switch (name) {
                case "getChannel" -> channel;
                case "getIdLong" -> 1L;
                case "getAuthor" -> author;
                case "getMember" -> member;
                case "getGuild" -> guild;
                case "getContentDisplay" -> content;
                case "isWebhookMessage" -> viaWebhook;
                case "getAttachments" -> List.of();
                default -> null;
            });
            return new MessageReceivedEvent(client, 1, message);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> behavior) {
        Class<?>[] interfaces = type == TextChannel.class
            ? new Class<?>[]{type, net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion.class, net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion.class}
            : new Class<?>[]{type};
        return (T) Proxy.newProxyInstance(type.getClassLoader(), interfaces, (proxy, method, args) -> {
            Object result = behavior.apply(method.getName(), args == null ? new Object[0] : args);
            if (result != null) return result;
            if (method.getReturnType().isInstance(proxy)) return proxy;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
    }
}
