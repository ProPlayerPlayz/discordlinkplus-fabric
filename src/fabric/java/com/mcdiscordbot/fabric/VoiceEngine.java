package com.mcdiscordbot.fabric;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Discord REST and bot management run off the Minecraft server thread. */
public final class VoiceEngine implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final BridgeConfig config;
    private final VoicechatServerApi api;
    private final Path stateFile;
    private final ChatBridge chat;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DiscordLinkPlus-rooms"); t.setDaemon(true); return t;
    });
    private final List<Bot> bots = new CopyOnWriteArrayList<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, OwnedChannel> owned = new HashMap<>();
    private volatile Map<String, Room> active = Map.of();
    private volatile Set<UUID> online = Set.of();
    private volatile boolean closed;
    private volatile String status = "Discord voice bridge starting.";
    private int failedBots;
    private int errorBackoff;

    public VoiceEngine(BridgeConfig config, VoicechatServerApi api, Path stateFile) {
        this.config = config; this.api = api; this.stateFile = stateFile;
        chat = new ChatBridge(config);
    }

    public void start() {
        chat.start();
        worker.execute(this::boot);
        worker.scheduleWithFixedDelay(this::reconcileSafely, 2, 1, TimeUnit.SECONDS);
    }

    public void players(Set<UUID> players) { online = players; }
    public String status() { return status + "\n" + chat.status(); }
    ChatRelay chat() { return config.chat.enabled ? chat.relay() : null; }
    boolean serverStarted() { return chat.relay().lifecycle(true); }

    public BridgePresence presence(UUID player) {
        if (closed || !online.contains(player)) return null;
        String key = key(api.getConnectionOf(player));
        Room room = key == null ? null : active.get(key);
        if (room == null || room.bot == null || room.bot.client.getStatus() != JDA.Status.CONNECTED) return null;
        Guild guild = room.bot.guild();
        if (guild == null) return null;
        var manager = guild.getAudioManager();
        var channel = manager.getConnectedChannel();
        if (!manager.isConnected() || channel == null || !room.channelId.equals(channel.getId())) return null;
        return new BridgePresence(room.audio.identity(), UUID.fromString(key));
    }

    public record BridgePresence(UUID identity, UUID group) {}

    public void microphone(VoicechatConnection sender, byte[] opus) {
        if (closed || sender == null || !online.contains(sender.getPlayer().getUuid())) return;
        String key = key(sender);
        Room room = key == null ? null : active.get(key);
        if (room != null) room.audio.microphone(sender.getPlayer().getUuid(), opus);
    }

    public void removeSpeaker(UUID player) { active.values().forEach(room -> room.audio.remove(player)); }

    private String key(VoicechatConnection connection) {
        if (!config.enabled) return null;
        if (connection == null || !connection.isConnected() || connection.isDisabled()) return null;
        Group group = connection.getGroup();
        return Routing.room(group == null ? null : group.getId(), group != null && group.hasPassword(), group != null && group.getType() == Group.Type.ISOLATED, config.bridgePasswordGroups);
    }

    private void boot() {
        try {
            if (config.enabled && Files.exists(stateFile)) {
                try (var reader = Files.newBufferedReader(stateFile)) {
                    Map<String, OwnedChannel> loaded = GSON.fromJson(reader, new TypeToken<Map<String, OwnedChannel>>() {}.getType());
                    if (loaded != null) owned.putAll(loaded);
                }
            }
        } catch (Exception e) {
            status = "Cannot read owned-channel registry; bridge stopped to avoid duplicating channels.";
            DiscordVoiceMod.LOG.error(status);
            closed = true;
            worker.shutdown();
            return;
        }
        int index = 0;
        for (String token : config.tokens()) {
            if (closed) return;
            int number = ++index;
            try {
                var intents = java.util.EnumSet.of(GatewayIntent.GUILD_VOICE_STATES);
                if (number == 1 && config.chat.enabled && config.chat.discordToMinecraft) {
                    intents.add(GatewayIntent.GUILD_MESSAGES);
                    intents.add(GatewayIntent.MESSAGE_CONTENT);
                }
                JDABuilder builder = JDABuilder.createDefault(token, intents)
                    .setMemberCachePolicy(MemberCachePolicy.VOICE)
                    .enableCache(CacheFlag.VOICE_STATE)
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                    .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()));
                if (number == 1 && config.chat.enabled) builder.addEventListeners(chat);
                JDA client = builder.build();
                if (number == 1) chat.bind(client);
                Bot bot = new Bot(number, client);
                bots.add(bot);
                if (closed) client.shutdownNow();
            } catch (Exception | LinkageError e) {
                failedBots++;
                DiscordVoiceMod.LOG.error("Voice bot {} could not start ({}). Check its token and native dependencies; no token is logged.", number, e.getClass().getSimpleName());
            }
        }
    }

    private void reconcileSafely() {
        if (closed) return;
        if (errorBackoff > 0) { errorBackoff--; return; }
        try { reconcile(); }
        catch (Exception e) {
            status = "Voice room reconciliation failed (" + e.getClass().getSimpleName() + "); retrying in 10 seconds. Check bot permissions, IDs, and connection.";
            DiscordVoiceMod.LOG.warn(status);
            errorBackoff = 10;
        }
    }

    private void reconcile() throws Exception {
        Bot manager = bots.stream().filter(b -> b.guild() != null && b.client.getStatus() == JDA.Status.CONNECTED).findFirst().orElse(null);
        if (manager == null) {
            status = "Waiting for Discord: " + bots.size() + " configured bots, " + failedBots + " startup failures. Verify bot invites and guildId.";
            return;
        }
        Guild guild = manager.guild();
        if (!config.enabled) {
            status = "Voice bridge disabled; text-only bot gateway: " + manager.client.getStatus() + ".";
            return;
        }
        if (guild.getCategoryById(config.groupCategoryId) == null) {
            status = "groupCategoryId is missing, inaccessible, or belongs to another Discord server.";
            return;
        }
        Map<String, String> desired = new LinkedHashMap<>();
        for (UUID id : online.stream().sorted().toList()) {
            VoicechatConnection connection = api.getConnectionOf(id);
            String key = key(connection);
            if (key != null) desired.put(key, connection.getGroup().getName());
        }

        // Drop audio routes before deleting/releasing rooms, so a departing group cannot receive stale audio.
        for (String key : new ArrayList<>(rooms.keySet())) {
            Room room = rooms.get(key);
            if (!desired.containsKey(key) || (room.bot != null && (room.bot.client.getStatus() == JDA.Status.SHUTDOWN || room.bot.guild() == null))) release(key);
        }
        for (String key : new ArrayList<>(owned.keySet())) {
            Group group = api.getGroup(UUID.fromString(key));
            boolean isolated = group != null && group.getType() == Group.Type.ISOLATED;
            if (!desired.containsKey(key) && (config.deleteEmptyGroupChannels || isolated)) deleteOwned(guild, key);
        }
        if (closed) return;

        for (var entry : desired.entrySet()) {
            String key = entry.getKey();
            Room room = rooms.get(key);
            VoiceChannel channel;
            {
                OwnedChannel record = owned.get(key);
                channel = record == null ? null : guild.getVoiceChannelById(record.channelId());
                if (channel != null && !Objects.equals(channel.getParentCategoryId(), record.categoryId())) {
                    // A manually moved channel is no longer ours to reuse/delete.
                    owned.remove(key); saveOwned(); channel = null;
                }
                if (channel == null) {
                    String name = channelName(entry.getValue());
                    channel = guild.getCategoryById(config.groupCategoryId).createVoiceChannel(name).submit().get(10, TimeUnit.SECONDS);
                    owned.put(key, new OwnedChannel(channel.getId(), config.groupCategoryId));
                    saveOwned();
                }
                String expectedName = channelName(entry.getValue());
                if (!channel.getName().equals(expectedName)) {
                    channel.getManager().setName(expectedName).submit().get(10, TimeUnit.SECONDS);
                }
            }
            if (closed) return;
            if (room != null && !room.channelId.equals(channel.getId())) { release(key); room = null; }
            if (room == null) {
                room = new Room(key, entry.getValue(), channel.getId());
                rooms.put(key, room);
            }
            room.name = entry.getValue();
            if (room.bot == null) {
                Bot available = bots.stream().filter(b -> b.room == null && b.guild() != null && b.client.getStatus() == JDA.Status.CONNECTED).findFirst().orElse(null);
                if (available != null) attach(room, available);
            }
            if (room.audio != null) room.audio.targets(online);
        }
        publish();
        int waiting = (int) rooms.values().stream().filter(r -> r.bot == null).count();
        List<String> lines = new ArrayList<>();
        lines.add("DiscordLink+ Voice: " + active.size() + " bridged rooms, " + waiting + " waiting for a bot, " + failedBots + " bot startup failures.");
        for (Bot bot : bots) lines.add("Bot " + bot.number + " gateway: " + bot.client.getStatus());
        for (Room room : rooms.values()) {
            lines.add(room.name + ": " + (room.bot == null ? "waiting for free bot" : "bot " + room.bot.number + ", " + room.bot.guild().getAudioManager().getConnectionStatus() + ", " + room.audio.metrics()));
        }
        status = String.join("\n", lines);
    }

    private void attach(Room room, Bot bot) {
        Guild guild = bot.guild();
        VoiceChannel channel = guild.getVoiceChannelById(room.channelId);
        if (channel == null) return;
        RoomAudio audio = new RoomAudio(api, connection -> !closed && connection != null && online.contains(connection.getPlayer().getUuid()) && Objects.equals(room.key, key(connection)));
        try {
            var manager = guild.getAudioManager();
            manager.setSelfDeafened(false);
            manager.setAutoReconnect(true);
            manager.setSendingHandler(audio);
            manager.setReceivingHandler(audio);
            manager.openAudioConnection(channel);
            room.audio = audio; room.bot = bot; bot.room = room.key;
            publish();
            DiscordVoiceMod.LOG.info("Bridging '{}' using voice bot {}.", room.name, bot.number);
        } catch (RuntimeException e) {
            guild.getAudioManager().setSendingHandler(null);
            guild.getAudioManager().setReceivingHandler(null);
            audio.close();
            throw e;
        }
    }

    private void publish() {
        Map<String, Room> next = new HashMap<>();
        if (!closed) rooms.forEach((key, room) -> { if (room.audio != null) next.put(key, room); });
        active = Map.copyOf(next);
    }

    private void release(String key) {
        Room room = rooms.remove(key);
        publish();
        if (room == null) return;
        if (room.bot != null) {
            Guild guild = room.bot.guild();
            if (guild != null) {
                var manager = guild.getAudioManager();
                manager.closeAudioConnection(); manager.setSendingHandler(null); manager.setReceivingHandler(null);
            }
            room.bot.room = null;
        }
        if (room.audio != null) room.audio.close();
    }

    private void deleteOwned(Guild guild, String key) throws Exception {
        OwnedChannel record = owned.get(key);
        VoiceChannel channel = guild.getVoiceChannelById(record.channelId());
        if (channel != null && Objects.equals(channel.getParentCategoryId(), record.categoryId())) {
            channel.delete().submit().get(10, TimeUnit.SECONDS);
        }
        owned.remove(key); saveOwned();
    }

    private void saveOwned() throws Exception {
        Path temp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(owned) + "\n");
        try { Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING); }
    }

    static String channelName(String name) {
        String base = name.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (base.isEmpty()) base = "Voice group";
        return base.length() > 100 ? base.substring(0, 100) : base;
    }

    @Override public void close() { close(false); }
    void close(boolean announceStop) {
        closed = true;
        chat.close(announceStop);
        Map<String, Room> previous = active;
        active = Map.of();
        previous.values().forEach(room -> room.audio.close());
        worker.shutdownNow();
        bots.forEach(bot -> bot.client.shutdownNow());
    }

    private final class Bot {
        final int number;
        final JDA client;
        String room;
        Bot(int number, JDA client) { this.number = number; this.client = client; }
        Guild guild() { return client.getGuildById(config.guildId); }
    }

    private static final class Room {
        final String key;
        String name;
        final String channelId;
        Bot bot;
        RoomAudio audio;
        Room(String key, String name, String channelId) { this.key = key; this.name = name; this.channelId = channelId; }
    }

    private record OwnedChannel(String channelId, String categoryId) {}
}
