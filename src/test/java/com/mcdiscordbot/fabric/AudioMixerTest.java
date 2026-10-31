package com.mcdiscordbot.fabric;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AudioMixerTest {
    private static short[] frame(int sample) { short[] f = new short[960]; Arrays.fill(f, (short) sample); return f; }

    @Test void simultaneousSpeakersAreMixedInOneFrame() {
        var mixer = new AudioMixer();
        mixer.offer(UUID.randomUUID(), frame(1000), 1);
        mixer.offer(UUID.randomUUID(), frame(-250), 1);
        assertArrayEquals(frame(750), mixer.poll(2));
        assertNull(mixer.poll(3));
    }

    @Test void clippingSaturatesRatherThanWrapping() {
        var mixer = new AudioMixer();
        mixer.offer(UUID.randomUUID(), frame(30000), 1);
        mixer.offer(UUID.randomUUID(), frame(30000), 1);
        assertArrayEquals(frame(32767), mixer.poll(2));
    }

    @Test void overloadDropsOldAudioAndStaleSpeechExpires() {
        var mixer = new AudioMixer();
        UUID player = UUID.randomUUID();
        for (int i = 1; i <= 8; i++) mixer.offer(player, frame(i), i);
        assertArrayEquals(frame(6), mixer.poll(9));
        assertArrayEquals(frame(7), mixer.poll(10));
        assertNull(mixer.poll(300_000_000L));
    }

    @Test void leavingRoomRemovesQueuedSpeech() {
        var mixer = new AudioMixer();
        UUID player = UUID.randomUUID();
        mixer.offer(player, frame(1000), 1);
        mixer.remove(player);
        assertNull(mixer.poll(2));
    }

    @Test void discordPcmIsBigEndianAndStereoDownmixDoesNotOverflow() {
        ByteBuffer stereo = ByteBuffer.allocate(3840);
        for (int i = 0; i < 960; i++) stereo.putShort((short) 30000).putShort((short) 10000);
        assertArrayEquals(frame(20000), AudioMixer.monoFromStereoBigEndian(stereo.array()));
        ByteBuffer encoded = AudioMixer.stereoBigEndian(frame(-1234));
        assertEquals(3840, encoded.remaining());
        assertEquals(-1234, encoded.getShort());
        assertEquals(-1234, encoded.getShort());
        assertNull(AudioMixer.monoFromStereoBigEndian(new byte[100]));
    }

    @Test void malformedFrameDoesNotEnterStream() {
        var mixer = new AudioMixer();
        mixer.offer(UUID.randomUUID(), new short[100], 1);
        assertNull(mixer.poll(2));
    }
}
