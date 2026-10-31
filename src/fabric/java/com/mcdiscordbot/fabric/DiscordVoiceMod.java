package com.mcdiscordbot.fabric;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DiscordVoiceMod implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("DiscordLinkPlusVoice");
    public static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("discordlinkplus-voice.json");
    private static volatile VoiceEngine engine;
    private static volatile VoicechatServerApi voicechat;
    private static MinecraftServer server;
    private static volatile boolean stopping, startupNoticePending;
    private static final DiscordRoster roster = new DiscordRoster();

    @Override public void onInitialize() {
        ChatEvents.register();
        try { BridgeConfig.load(CONFIG); }
        catch (Exception e) { LOG.error("Cannot load config/discordlinkplus-voice.json; bridge remains disabled."); }
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; stopping = false; startupNoticePending = true; reload(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { stopping = true; stop(true); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; voicechat = null; roster.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            VoiceEngine current = engine;
            if (current != null) {
                Set<UUID> ids = s.getPlayerList().getPlayers().stream().map(p -> p.getUUID()).collect(Collectors.toUnmodifiableSet());
                current.players(ids);
            }
            roster.tick(s, current);
            ChatEvents.tick(s);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
          for (String name : new String[]{"discordlink", "discordvoice"}) dispatcher.register(
            Commands.literal(name)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("reload").executes(ctx -> {
                    reload();
                    ctx.getSource().sendSuccess(() -> Component.literal("DiscordLink+ config reloaded; use /discordlink status."), false);
                    return 1;
                }))
          );
        });
        LOG.info("DiscordLink+ Fabric 26.3 loaded. Config: config/discordlinkplus-voice.json");
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        VoiceEngine current = engine;
        String value = current == null ? "Discord bridge disabled. Configure config/discordlinkplus-voice.json and /discordlink reload." : current.status();
        source.sendSuccess(() -> Component.literal(value), false);
        return 1;
    }

    public static VoiceEngine engine() { return engine; }
    public static void refreshRoster(UUID player) { roster.refresh(player); }
    public static synchronized void voicechatStarted(VoicechatServerApi api) { voicechat = api; if (server != null) reload(); }
    public static synchronized void voicechatStopped() {
        voicechat = null;
        // SVC may stop before our SERVER_STOPPING listener. Keep Discord alive for its stop notice.
        if (server == null) stop();
    }

    private static synchronized void reload() {
        BridgeConfig config;
        try { config = BridgeConfig.load(CONFIG); }
        catch (Exception e) { LOG.error("Invalid DiscordLink+ config; keeping current bridge. Check JSON, IDs and token placeholders."); return; }
        stop();
        if (!config.enabled && !config.chat.enabled) { LOG.info("Discord bridge is disabled in config."); return; }
        if (voicechat == null) { LOG.info("Waiting for Simple Voice Chat server API."); return; }
        engine = new VoiceEngine(config, voicechat, FabricLoader.getInstance().getConfigDir().resolve("discordlinkplus-voice-channels.json"));
        if (startupNoticePending && engine.serverStarted()) startupNoticePending = false;
        engine.start();
    }

    private static synchronized void stop() { stop(false); }
    private static synchronized void stop(boolean announceStop) {
        VoiceEngine previous = engine;
        engine = null;
        if (previous != null) previous.close(announceStop);
    }
}
