package stonytark.cinemarr.core.protocol;

/** Shared required-client capability and transport-limit negotiation. */
public final class ProtocolCapabilities {
    public static final long FEATURE_VIDEO_SEGMENTS = 1L;
    public static final long FEATURE_CLIENT_HEALTH = 1L << 1;
    public static final long REQUIRED_FEATURES = FEATURE_VIDEO_SEGMENTS | FEATURE_CLIENT_HEALTH;
    public static final int MAX_TRANSFER_WINDOW = 8;
    public static final int HEALTH_INTERVAL_MS = 1_000;
    public static final int MIN_CHUNK_BYTES = 1_024;
    public static final int MIN_HEALTH_INTERVAL_MS = 250;
    public static final int MAX_HEALTH_INTERVAL_MS = 5_000;

    public static Offer currentOffer() {
        return new Offer(ProtocolLimits.VERSION, REQUIRED_FEATURES, ProtocolLimits.MAX_VIDEO_CHUNK_BYTES,
                MAX_TRANSFER_WINDOW, HEALTH_INTERVAL_MS);
    }

    public static Negotiated negotiate(int version, long features, int maxChunkBytes,
                                       int maxTransferWindow, int healthIntervalMs) {
        if (version != ProtocolLimits.VERSION) throw new IllegalArgumentException(
                "protocol mismatch: server requires version " + ProtocolLimits.VERSION);
        if ((features & REQUIRED_FEATURES) != REQUIRED_FEATURES) throw new IllegalArgumentException(
                "required Cinemarr video capabilities are missing");
        if (maxChunkBytes < ProtocolLimits.MAX_VIDEO_CHUNK_BYTES || maxTransferWindow < MAX_TRANSFER_WINDOW
                || healthIntervalMs < MIN_HEALTH_INTERVAL_MS || healthIntervalMs > MAX_HEALTH_INTERVAL_MS) {
            throw new IllegalArgumentException("invalid Cinemarr capability limits");
        }
        return new Negotiated(features & REQUIRED_FEATURES,
                ProtocolLimits.MAX_VIDEO_CHUNK_BYTES, MAX_TRANSFER_WINDOW, healthIntervalMs);
    }

    public static final class Offer {
        private final int version; private final long features; private final int maxChunkBytes;
        private final int maxTransferWindow; private final int healthIntervalMs;
        private Offer(int version, long features, int maxChunkBytes, int maxTransferWindow, int healthIntervalMs) {
            this.version=version;this.features=features;this.maxChunkBytes=maxChunkBytes;
            this.maxTransferWindow=maxTransferWindow;this.healthIntervalMs=healthIntervalMs;
        }
        public int version(){return version;} public long features(){return features;}
        public int maxChunkBytes(){return maxChunkBytes;} public int maxTransferWindow(){return maxTransferWindow;}
        public int healthIntervalMs(){return healthIntervalMs;}
    }

    public static final class Negotiated {
        private final long features; private final int maxChunkBytes, maxTransferWindow, healthIntervalMs;
        private Negotiated(long features,int maxChunkBytes,int maxTransferWindow,int healthIntervalMs){
            this.features=features;this.maxChunkBytes=maxChunkBytes;this.maxTransferWindow=maxTransferWindow;this.healthIntervalMs=healthIntervalMs;
        }
        public long features(){return features;} public int maxChunkBytes(){return maxChunkBytes;}
        public int maxTransferWindow(){return maxTransferWindow;} public int healthIntervalMs(){return healthIntervalMs;}
    }

    private ProtocolCapabilities() {}
}
