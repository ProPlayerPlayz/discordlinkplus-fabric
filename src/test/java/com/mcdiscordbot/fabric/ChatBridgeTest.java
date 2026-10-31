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
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
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

    @Test void webhookUsesPlayerIdentityAndNoMentionsAndSystemEventsUseServerIdentity() {
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
            assertEquals(List.of("hello @everyone", "Friend joined"), network.webhookMessages);
            assertTrue(network.botMessages.isEmpty());
            assertEquals("Minecraft Server", network.username);
            assertEquals(1, network.avatarOverrides);
            assertEquals(List.of(), network.mentions);
        }
    }

    @Test void existingWebhookUsesServerIconForAllNonPlayerAnnouncements(@TempDir Path temp) throws Exception {
        var image = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Path icon = temp.resolve("server-icon.png");
        javax.imageio.ImageIO.write(image, "png", icon.toFile());
        var config = config(); config.chat.serverName = "Axolotl";
        var network = new Network(false);
        try (var bridge = new ChatBridge(config, icon)) {
            bridge.bind(network.client);
            for (String key : List.of("multiplayer.player.joined", "multiplayer.player.left", "death.attack.generic", "chat.type.advancement.task")) {
                bridge.relay().game(key, "event " + key); bridge.work();
                assertEquals("Axolotl", network.username);
            }
            assertNotNull(network.defaultAvatar);
            assertEquals(Icon.from(icon.toFile()).getEncoding(), network.defaultAvatar.getEncoding());
            assertEquals(0, network.avatarOverrides);
            assertTrue(network.botMessages.isEmpty());
            assertTrue(bridge.status().contains("Server announcement avatar: server-icon.png"));
        }
    }

    @Test void newWebhookReceivesServerIconAndNormalShutdownFlushesItsStopMessage(@TempDir Path temp) throws Exception {
        Path icon = temp.resolve("server-icon.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 64, 2), "png", icon.toFile());
        var network = new Network(false, false);
        try (var bridge = new ChatBridge(config(), icon)) {
            bridge.bind(network.client);
            assertTrue(bridge.relay().lifecycle(true));
            bridge.work();
            assertNotNull(network.defaultAvatar);
            assertEquals(List.of("Server started."), network.webhookMessages);
            bridge.close(true);
            assertEquals(List.of("Server started.", "Server stopped."), network.webhookMessages);
            assertEquals(0, network.avatarOverrides);
            assertTrue(network.botMessages.isEmpty());
        }
        assertEquals(2, network.webhookMessages.size());
    }

    @Test void reloadCloseDoesNotAnnounceAServerStopAndInvalidIconsDoNotBreakDelivery(@TempDir Path temp) throws Exception {
        Path icon = temp.resolve("server-icon.png"); java.nio.file.Files.writeString(icon, "not a PNG");
        var network = new Network(false);
        try (var bridge = new ChatBridge(config(), icon)) {
            bridge.bind(network.client);
            bridge.relay().game("multiplayer.player.left", "Friend left"); bridge.work();
            assertTrue(bridge.status().contains("unreadable"));
        }
        assertEquals(List.of("Friend left"), network.webhookMessages);
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
            assertTrue(bridge.status().contains("Discord events=3, channel matches=3, ignored bots/webhooks=2"));
        }
    }

    @Test void aNewWebhookUsesAnApiAllowedNameAndDeliversTheFirstMessage() {
        var network = new Network(false, false);
        try (var bridge = new ChatBridge(config())) {
            bridge.bind(network.client);
            bridge.relay().minecraft(UUID.randomUUID(), "Player", null, "first message");
            bridge.work();
            assertEquals("MC Link Chat", network.createdWebhookName);
            assertEquals(List.of("first message"), network.webhookMessages);
            assertTrue(bridge.status().contains("player webhooks"));
        }
    }

    @Test void reservedPlayerNamesUseBotFallbackWithoutLosingThePlayerName() {
        var network = new Network(false);
        try (var bridge = new ChatBridge(config())) {
            bridge.bind(network.client);
            bridge.relay().minecraft(UUID.randomUUID(), "ClydePlayer", null, "hello");
            bridge.work();
            assertEquals(List.of("**ClydePlayer**: hello"), network.botMessages);
            assertTrue(network.webhookMessages.isEmpty());
        }
    }

    private static final class Network {
        final List<String> botMessages = new ArrayList<>(), webhookMessages = new ArrayList<>();
        Object mentions;
        Icon defaultAvatar;
        int avatarOverrides;
        String username, avatar, createdWebhookName;
        final SelfUser self = stub(SelfUser.class, (name, args) -> name.equals("getId") ? "bot" : null);
        final Webhook webhook;
        final TextChannel channel;
        Guild guild;
        final JDA client;

        Network(boolean failWebhookSetup) {
            this(failWebhookSetup, true);
        }

        Network(boolean failWebhookSetup, boolean existingWebhook) {
            webhook = stub(Webhook.class, (name, args) -> switch (name) {
                case "getType" -> WebhookType.INCOMING;
                case "getName" -> "MC Link Chat";
                case "getOwnerAsUser" -> self;
                case "getManager" -> stub(net.dv8tion.jda.api.managers.WebhookManager.class, (method, values) -> {
                    if (method.equals("setAvatar")) defaultAvatar = (Icon) values[0];
                    return method.equals("submit") ? CompletableFuture.completedFuture(null) : null;
                });
                case "sendMessage" -> { webhookMessages.add(args[0].toString()); yield sendAction(WebhookMessageCreateAction.class); }
                default -> null;
            });
            channel = stub(TextChannel.class, (name, args) -> switch (name) {
                case "getId" -> CHANNEL;
                case "getType" -> ChannelType.TEXT;
                case "getGuild" -> this.guild;
                case "retrieveWebhooks" -> {
                    if (failWebhookSetup) throw new IllegalStateException("permission denied");
                    yield stub(RestAction.class, (method, ignored) -> method.equals("submit") ? CompletableFuture.completedFuture(existingWebhook ? List.of(webhook) : List.of()) : null);
                }
                case "createWebhook" -> {
                    String supplied = args[0].toString();
                    if (supplied.toLowerCase(Locale.ROOT).contains("discord") || supplied.toLowerCase(Locale.ROOT).contains("clyde"))
                        throw new IllegalArgumentException("Reserved webhook name");
                    createdWebhookName = supplied;
                    yield stub(net.dv8tion.jda.api.requests.restaction.WebhookAction.class, (method, values) -> {
                        if (method.equals("setAvatar")) defaultAvatar = (Icon) values[0];
                        return method.equals("submit") ? CompletableFuture.completedFuture(webhook) : null;
                    });
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
                case "setAvatarUrl" -> { avatarOverrides++; avatar = args[0].toString(); yield null; }
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
