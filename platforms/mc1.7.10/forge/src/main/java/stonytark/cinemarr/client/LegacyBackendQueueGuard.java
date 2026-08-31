package stonytark.cinemarr.client;

/** Prevents a stopped or replaced Paulscode source from receiving a stale queue. */
final class LegacyBackendQueueGuard {
    static boolean shouldRecover(boolean paused,boolean terminal,boolean playing,long nowMs,long activationGraceUntilMs){return !paused&&!terminal&&!playing&&nowMs>=activationGraceUntilMs;}
    private LegacyBackendQueueGuard(){}
}
