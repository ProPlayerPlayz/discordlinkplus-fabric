package com.mcdiscordbot.fabric;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.*;

public final class SvcPlugin implements VoicechatPlugin {
    @Override public String getPluginId() { return "discordlinkplus_voice"; }

    @Override public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> DiscordVoiceMod.voicechatStarted(event.getVoicechat()));
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> DiscordVoiceMod.voicechatStopped());
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            var engine = DiscordVoiceMod.engine();
            if (engine != null && !event.isCancelled()) engine.microphone(event.getSenderConnection(), event.getPacket().getOpusEncodedData());
        }, -100);
        registration.registerEvent(JoinGroupEvent.class, event -> {
            var engine = DiscordVoiceMod.engine();
            if (engine != null) engine.removeSpeaker(event.getConnection().getPlayer().getUuid());
        });
        registration.registerEvent(LeaveGroupEvent.class, event -> {
            var engine = DiscordVoiceMod.engine();
            if (engine != null) engine.removeSpeaker(event.getConnection().getPlayer().getUuid());
        });
    }
}
