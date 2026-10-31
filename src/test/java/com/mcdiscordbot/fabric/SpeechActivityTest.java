package com.mcdiscordbot.fabric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpeechActivityTest {
    @Test void rejectsSilenceAndQuietNoise() {
        var activity = new SpeechActivity();
        assertFalse(activity.speaking(0));
        activity.accept(new short[960], 0);
        activity.accept(new short[]{20, -20}, 1);
        activity.accept(new short[0], 2);
        assertFalse(activity.speaking(2));
    }
    @Test void holdExpiresWithoutSilenceRefreshingIt() {
        var activity = new SpeechActivity();
        activity.accept(new short[]{-200, 200}, 1_000);
        assertTrue(activity.speaking(1_000));
        activity.accept(new short[960], 200_001_000);
        assertTrue(activity.speaking(249_001_000));
        assertFalse(activity.speaking(250_001_000));
    }
    @Test void newSpeechRefreshesHold() {
        var activity = new SpeechActivity();
        activity.accept(new short[]{Short.MIN_VALUE}, 1_000);
        activity.accept(new short[]{Short.MAX_VALUE}, 200_001_000);
        assertTrue(activity.speaking(400_001_000));
        assertFalse(activity.speaking(450_001_000));
    }
}
