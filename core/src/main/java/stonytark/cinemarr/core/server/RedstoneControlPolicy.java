package stonytark.cinemarr.core.server;

/** Rising-edge policy for an adjacent television redstone receiver. */
public final class RedstoneControlPolicy {
    public enum Action { NONE, PAUSE, RESUME }

    public static Action action(boolean wasPowered, boolean powered, boolean hasMedia, boolean paused) {
        if (wasPowered || !powered || !hasMedia) return Action.NONE;
        return paused ? Action.RESUME : Action.PAUSE;
    }

    private RedstoneControlPolicy() {}
}
