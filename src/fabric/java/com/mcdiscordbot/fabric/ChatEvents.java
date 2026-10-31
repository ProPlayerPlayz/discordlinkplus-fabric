package com.mcdiscordbot.fabric;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;

/** Relay only accepted public player chat and the selected vanilla global announcements. */
final class ChatEvents {
    static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, bound) -> {
            ChatRelay relay = relay();
            if (relay == null) return;
            relay.minecraftEvent();
            if (!bound.chatType().is(ChatType.CHAT) || message.isFullyFiltered()) return;
            String text = message.filterMask().isEmpty() ? message.decoratedContent().getString() : message.filterMask().apply(message.signedContent());
            var profile = sender.getGameProfile();
            // Skin Restorer replaces this profile when a skin changes. Resolve it per message,
            // rather than looking up the offline UUID or caching an old texture.
            String texture = profile.properties().get("textures").stream().map(property -> property.value()).findFirst().orElse(null);
            relay.minecraft(sender.getUUID(), profile.name(), texture, text);
        });
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            ChatRelay relay = relay();
            if (relay != null && !overlay && message.getContents() instanceof TranslatableContents translated)
                relay.game(translated.getKey(), message.getString());
        });
    }

    static void tick(MinecraftServer server) {
        ChatRelay relay = relay();
        if (relay == null) return;
        for (int count = 0; count < 5; count++) {
            ChatRelay.Incoming message = relay.nextIncoming();
            if (message == null) break;
            server.getPlayerList().broadcastSystemMessage(Component.literal(message.text()), false);
            relay.received();
        }
    }

    private static ChatRelay relay() {
        VoiceEngine engine = DiscordVoiceMod.engine();
        return engine == null ? null : engine.chat();
    }
}
