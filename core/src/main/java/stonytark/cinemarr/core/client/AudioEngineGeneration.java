package stonytark.cinemarr.core.client;

import java.util.concurrent.atomic.AtomicLong;

/** Identifies native sound-engine lifetimes independently of media streams. */
public final class AudioEngineGeneration {
    private static final AtomicLong GENERATION = new AtomicLong();
    public static long current() { return GENERATION.get(); }
    public static void destroying() { GENERATION.incrementAndGet(); }
    private AudioEngineGeneration() {}
}
