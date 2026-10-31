package com.mcdiscordbot.fabric;

import com.google.gson.Gson;
import de.maxhenkel.voicechat.net.NetManager;
import de.maxhenkel.voicechat.net.PlayerStatePacket;
import de.maxhenkel.voicechat.net.RemovePlayerStatePacket;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only player-info and SVC roster packets. No world entity, login, or saved player. */
final class DiscordRoster {
    private static final Skin SKIN = loadSkin();
    private final Map<UUID, Shown> shown = new HashMap<>();
    private final Set<UUID> refresh = ConcurrentHashMap.newKeySet();

    void refresh(UUID player) { refresh.add(player); }

    void tick(MinecraftServer server, VoiceEngine engine) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean resend = refresh.remove(player.getUUID());
            VoiceEngine.BridgePresence next = engine == null ? null : engine.presence(player.getUUID());
            Shown previous = shown.get(player.getUUID());
            // A fresh network handler must always get its own profile and roster state.
            if (previous != null && previous.connection != player.connection) {
                shown.remove(player.getUUID()); previous = null;
            }
            if (previous != null && !previous.presence.equals(next)) {
                remove(player, previous.presence.identity());
                shown.remove(player.getUUID()); previous = null;
            }
            if (next == null) continue;
            if (previous == null) {
                player.connection.send(profilePacket(server.registryAccess(), next.identity()));
                shown.put(player.getUUID(), new Shown(player.connection, next));
            }
            // SVC sends a complete state snapshot during voice reconnects; restore our UI entry afterwards.
            if (previous == null || resend) {
                PlayerState state = new PlayerState(next.identity(), "Discord", false, false);
                state.setGroup(next.group());
                NetManager.sendToClient(player, new PlayerStatePacket(state));
            }
        }
        shown.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        refresh.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    void clear() { shown.clear(); refresh.clear(); }

    private static void remove(ServerPlayer player, UUID identity) {
        NetManager.sendToClient(player, new RemovePlayerStatePacket(identity));
        player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(identity)));
    }

    static ClientboundPlayerInfoUpdatePacket profilePacket(RegistryAccess registries, UUID identity) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            // Decode the pinned 26.3 public packet codec: no reflection or fabricated ServerPlayer.
            buffer.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), ClientboundPlayerInfoUpdatePacket.Action.class);
            buffer.writeVarInt(1);
            buffer.writeUUID(identity);
            buffer.writeUtf("Discord", 16);
            buffer.writeVarInt(1);
            buffer.writeUtf("textures");
            buffer.writeUtf(SKIN.value);
            buffer.writeBoolean(true);
            buffer.writeUtf(SKIN.signature);
            buffer.writeBoolean(false); // Available for skin lookups, hidden from the TAB player list.
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
        } finally { buffer.release(); }
    }

    private static Skin loadSkin() {
        try (var input = DiscordRoster.class.getResourceAsStream("/discord-head.json")) {
            if (input == null) throw new IllegalStateException("Missing Discord skin");
            return new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Skin.class);
        } catch (Exception e) { throw new IllegalStateException("Cannot load bundled Discord skin", e); }
    }
    private record Skin(String value, String signature) {}
    private record Shown(Object connection, VoiceEngine.BridgePresence presence) {}
}
