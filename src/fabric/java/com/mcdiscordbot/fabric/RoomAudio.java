package com.mcdiscordbot.fabric;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.User;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Codecs are room-local; SVC player connections are never impersonated or replaced. */
public final class RoomAudio implements AudioSendHandler, AudioReceiveHandler, AutoCloseable {
    private final VoicechatServerApi api;
    private final Predicate<VoicechatConnection> accepts;
    private final AudioMixer mixer = new AudioMixer();
    private final SpeechActivity discordSpeech = new SpeechActivity();
    private final Map<UUID, Decoder> decoders = new HashMap<>();
    private final OpusEncoder encoder;
    private final StaticAudioChannel channel;
    private final UUID identity = UUID.randomUUID();
    private ByteBuffer pending;
    private volatile boolean closed;
    private long receivedFrames;
    private long sentFrames;
    private long codecErrors;

    public RoomAudio(VoicechatServerApi api, Predicate<VoicechatConnection> accepts) {
        this.api = api;
        this.accepts = accepts;
        encoder = api.createEncoder();
        channel = api.createStaticAudioChannel(identity);
        channel.setBypassGroupIsolation(true);
        channel.setFilter(player -> accepts.test(api.getConnectionOf(player.getUuid())));
    }

    public synchronized void microphone(UUID player, byte[] opus) {
        if (closed || opus == null || opus.length == 0) return;
        long now = System.nanoTime();
        Decoder decoder = decoders.computeIfAbsent(player, k -> new Decoder(api.createDecoder()));
        try {
            if (now - decoder.last > 1_000_000_000L) decoder.codec.resetState();
            decoder.last = now;
            mixer.offer(player, decoder.codec.decode(opus), now);
        } catch (RuntimeException e) { codecErrors++; }
    }

    public synchronized void remove(UUID player) {
        mixer.remove(player);
        Decoder removed = decoders.remove(player);
        if (removed != null) removed.codec.close();
        pending = null;
    }

    public synchronized void targets(Set<UUID> players) {
        if (closed) return;
        channel.clearTargets();
        for (UUID player : players) {
            VoicechatConnection connection = api.getConnectionOf(player);
            if (connection != null && accepts.test(connection)) channel.addTarget(connection);
        }
        long now = System.nanoTime();
        var iterator = decoders.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!players.contains(entry.getKey()) || !accepts.test(api.getConnectionOf(entry.getKey())) || now - entry.getValue().last > 30_000_000_000L) {
                mixer.remove(entry.getKey());
                entry.getValue().codec.close();
                iterator.remove();
            }
        }
    }

    @Override public synchronized boolean canProvide() {
        if (closed) return false;
        if (pending == null) {
            short[] samples = mixer.poll(System.nanoTime());
            if (samples != null) pending = AudioMixer.stereoBigEndian(samples);
        }
        return pending != null;
    }

    @Override public synchronized ByteBuffer provide20MsAudio() {
        ByteBuffer result = pending;
        pending = null;
        if (result != null) sentFrames++;
        return result;
    }
    @Override public boolean isOpus() { return false; }
    @Override public boolean canReceiveCombined() { return true; }
    @Override public boolean includeUserInCombinedAudio(User user) { return !user.isBot(); }

    @Override public synchronized void handleCombinedAudio(CombinedAudio audio) {
        if (closed || audio.getUsers().isEmpty()) return;
        short[] mono = AudioMixer.monoFromStereoBigEndian(audio.getAudioData(1.0));
        if (mono == null) return;
        discordSpeech.accept(mono, System.nanoTime());
        if (!discordSpeech.speaking(System.nanoTime())) return;
        try { channel.send(encoder.encode(mono)); receivedFrames++; }
        catch (RuntimeException e) { codecErrors++; }
    }

    public synchronized String metrics() { return "MC→Discord=" + sentFrames + ", Discord→MC=" + receivedFrames + ", codecErrors=" + codecErrors; }
    public UUID identity() { return identity; }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        channel.clearTargets();
        channel.flush();
        encoder.close();
        decoders.values().forEach(d -> d.codec.close());
        decoders.clear();
        mixer.clear();
        pending = null;
    }

    private static final class Decoder {
        final OpusDecoder codec;
        long last;
        Decoder(OpusDecoder codec) { this.codec = codec; }
    }
}
