package com.mcdiscordbot.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DiscordRosterTest {
    @Test void profileIsUnlistedSignedAndRoundTripsThroughTheActualMinecraftCodec() {
        UUID identity = UUID.randomUUID();
        var packet = DiscordRoster.profilePacket(RegistryAccess.EMPTY, identity);
        var entry = packet.entries().getFirst();
        assertEquals(identity, entry.profileId());
        assertEquals("Discord", entry.profile().name());
        assertFalse(entry.listed());
        var texture = entry.profile().properties().get("textures").iterator().next();
        assertTrue(texture.hasSignature());
        String data = new String(java.util.Base64.getDecoder().decode(texture.value()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(data.contains("textures.minecraft.net/texture/"));
        assertTrue(packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
            assertEquals(entry, decoded.entries().getFirst());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
