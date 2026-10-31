package com.mcdiscordbot.fabric;

/** A noise floor and 250 ms release prevent silent Discord frames and flicker. */
final class SpeechActivity {
    private static final long HOLD = 250_000_000L;
    private volatile long lastSignal = Long.MIN_VALUE;
    void accept(short[] samples, long now) {
        if (samples.length == 0) return;
        double energy = 0;
        for (short sample : samples) energy += (double) sample * sample;
        if (energy / samples.length >= 10_000D) lastSignal = now; // RMS 100, about -50 dBFS
    }
    boolean speaking(long now) {
        long last = lastSignal;
        return last != Long.MIN_VALUE && now - last >= 0 && now - last < HOLD;
    }
}
