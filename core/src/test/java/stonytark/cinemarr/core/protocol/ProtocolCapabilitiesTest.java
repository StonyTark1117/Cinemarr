package stonytark.cinemarr.core.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolCapabilitiesTest {
    @Test void currentOfferNegotiatesCanonicalLimits() {
        ProtocolCapabilities.Offer offer=ProtocolCapabilities.currentOffer();
        ProtocolCapabilities.Negotiated value=ProtocolCapabilities.negotiate(offer.version(),offer.features(),offer.maxChunkBytes(),offer.maxTransferWindow(),offer.healthIntervalMs());
        assertEquals(ProtocolLimits.MAX_VIDEO_CHUNK_BYTES,value.maxChunkBytes());
        assertEquals(ProtocolCapabilities.MAX_TRANSFER_WINDOW,value.maxTransferWindow());
        assertEquals(ProtocolCapabilities.REQUIRED_FEATURES,value.features());
    }

    @Test void clampsSafeOversizedLimits() {
        ProtocolCapabilities.Negotiated value=ProtocolCapabilities.negotiate(ProtocolLimits.VERSION,
                ProtocolCapabilities.REQUIRED_FEATURES,1_000_000,1_000,2_000);
        assertEquals(ProtocolLimits.MAX_VIDEO_CHUNK_BYTES,value.maxChunkBytes());
        assertEquals(ProtocolCapabilities.MAX_TRANSFER_WINDOW,value.maxTransferWindow());
    }

    @Test void rejectsMismatchMissingFeaturesAndMalformedLimits() {
        assertThrows(IllegalArgumentException.class,()->ProtocolCapabilities.negotiate(ProtocolLimits.VERSION-1,
                ProtocolCapabilities.REQUIRED_FEATURES,16_384,8,1_000));
        assertThrows(IllegalArgumentException.class,()->ProtocolCapabilities.negotiate(ProtocolLimits.VERSION,
                ProtocolCapabilities.FEATURE_VIDEO_SEGMENTS,16_384,8,1_000));
        assertThrows(IllegalArgumentException.class,()->ProtocolCapabilities.negotiate(ProtocolLimits.VERSION,
                ProtocolCapabilities.REQUIRED_FEATURES,-1,Integer.MIN_VALUE,Integer.MAX_VALUE));
    }
}
