package com.mcdiscordbot.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One independently buffered mono stream per speaker, mixed at 48 kHz / 20 ms. */
public final class AudioMixer {
    public static final int SAMPLES = 960;
    private static final int MAX_QUEUED = 3;
    private static final long STALE_NANOS = 200_000_000L;
    private final Map<UUID, Stream> streams = new HashMap<>();

    public synchronized void offer(UUID speaker, short[] pcm, long now) {
        if (pcm == null || pcm.length == 0 || pcm.length % SAMPLES != 0 || pcm.length > 5760) return;
        Stream stream = streams.computeIfAbsent(speaker, k -> new Stream());
        if (now - stream.last > STALE_NANOS) stream.frames.clear();
        stream.last = now;
        for (int offset = 0; offset < pcm.length; offset += SAMPLES) {
            while (stream.frames.size() >= MAX_QUEUED) stream.frames.removeFirst();
            stream.frames.addLast(java.util.Arrays.copyOfRange(pcm, offset, offset + SAMPLES));
        }
    }

    public synchronized short[] poll(long now) {
        int[] mixed = new int[SAMPLES];
        boolean found = false;
        var iterator = streams.entrySet().iterator();
        while (iterator.hasNext()) {
            Stream stream = iterator.next().getValue();
            if (now - stream.last > STALE_NANOS) { iterator.remove(); continue; }
            short[] frame = stream.frames.pollFirst();
            if (frame == null) continue;
            found = true;
            for (int i = 0; i < SAMPLES; i++) mixed[i] += frame[i];
        }
        if (!found) return null;
        short[] output = new short[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) output[i] = (short) Math.clamp(mixed[i], Short.MIN_VALUE, Short.MAX_VALUE);
        return output;
    }

    public synchronized void remove(UUID speaker) { streams.remove(speaker); }
    public synchronized void clear() { streams.clear(); }

    public static ByteBuffer stereoBigEndian(short[] mono) {
        ByteBuffer output = ByteBuffer.allocate(mono.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (short sample : mono) { output.putShort(sample); output.putShort(sample); }
        return output.flip();
    }

    public static short[] monoFromStereoBigEndian(byte[] stereo) {
        if (stereo.length != SAMPLES * 4) return null;
        ByteBuffer input = ByteBuffer.wrap(stereo).order(ByteOrder.BIG_ENDIAN);
        short[] output = new short[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) output[i] = (short) (((int) input.getShort() + input.getShort()) / 2);
        return output;
    }

    private static final class Stream {
        final ArrayDeque<short[]> frames = new ArrayDeque<>();
        long last;
    }
}
