package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.ClockSynchronizer;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.protocol.AcceptanceControlFile;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;

import java.util.concurrent.atomic.AtomicLong;

/** Client media clock and television payload dispatcher for Forge 1.7.10. */
final class LegacyClientState implements LegacyNetwork.ClientListener {
    private static final int STARTUP_CLOCK_MIN_SAMPLES = 8;
    private static final long STARTUP_CLOCK_MAX_ROUND_TRIP_MS = 150L;
    private static final long STARTUP_CLOCK_SYNC_INTERVAL_MS = 250L;
    private static final long STEADY_CLOCK_SYNC_INTERVAL_MS = 10_000L;
    static final LegacyClientState INSTANCE = new LegacyClientState();

    private final ClockSynchronizer clock = new ClockSynchronizer();
    private final AcceptanceControlFile acceptanceControl = new AcceptanceControlFile();
    private final stonytark.cinemarr.core.protocol.AcceptanceSegmentPeer segmentPeer = new stonytark.cinemarr.core.protocol.AcceptanceSegmentPeer();
    private final AtomicLong timeNonce = new AtomicLong();
    private boolean helloSent;
    private boolean helloConfirmed;
    private boolean commandProbeSent;
    private boolean operatorProbeSent;
    private long lastTimeSync;
    private long acceptanceVideoController;
    private boolean acceptanceVideoTuneSent;
    private boolean acceptanceVideoResetSent;
    private boolean acceptanceVideoLibrariesRequested;
    private boolean acceptanceVideoBrowseRequested;
    private boolean acceptanceVideoPlaySent;
    private String acceptanceVideoLibraryId = "";
    private String acceptanceVideoLastItemKey = "";

    @Override public void accept(LegacyPacketTypes.Type<?> type, Object message) {
        if (ProtocolLimits.segmentPressurePeerEnabled()) {
            if (type == LegacyPacketTypes.VIDEO_SESSION_STATE) {
                acceptVideoProbe(type, message);
                VideoPackets.SessionState state = (VideoPackets.SessionState) message;
                segmentPeer.session(state.identity(), state.canControl(), state.status() == VideoPackets.SessionStatus.PLAYING);
                return;
            }
            if (type == LegacyPacketTypes.VIDEO_SEGMENT_CHUNK) { segmentPeer.chunk((VideoPackets.SegmentChunk) message, this::sendSegmentPeer); return; }
            if (type == LegacyPacketTypes.ERROR) { segmentPeer.error(((LegacyPacketTypes.ErrorMessage) message).message()); return; }
        }
        if (LegacyVideoClientState.INSTANCE.accept(type, message)) {
            acceptVideoProbe(type, message);
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (type == LegacyPacketTypes.SERVER_HELLO) {
            LegacyPacketTypes.ServerHello hello = (LegacyPacketTypes.ServerHello) message;
            if (!hello.valid() || hello.protocolVersion() != ProtocolLimits.clientHelloVersion()) {
                if (minecraft.getNetHandler() != null) minecraft.getNetHandler().getNetworkManager().closeChannel(
                        new ChatComponentText("Cinemarr protocol mismatch: server requires version " + hello.protocolVersion()));
            } else {
                helloConfirmed = true;
                requestTimeSync();
            }
        } else if (type == LegacyPacketTypes.TIME_SYNC_RESPONSE) {
            LegacyPacketTypes.TimeSyncResponse value = (LegacyPacketTypes.TimeSyncResponse) message;
            ClockSynchronizer.Sample sample = clock.accept(value.clientSentEpochMs(), value.serverEpochMs(), System.currentTimeMillis());
            if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance media clock: sample={} roundTripMs={} rawOffsetMs={} filteredOffsetMs={} bestRoundTripMs={}",
                    clock.sampleCount(), sample.roundTripMs(), sample.rawOffsetMs(), sample.filteredOffsetMs(), clock.bestRoundTripMs());
        } else if (type == LegacyPacketTypes.ERROR && minecraft.thePlayer != null) {
            if (ProtocolLimits.browsePressureProbeEnabled()) Cinemarr.LOGGER.info("Acceptance video request error: message={}", ((LegacyPacketTypes.ErrorMessage) message).message());
            minecraft.thePlayer.addChatMessage(new ChatComponentText("Cinemarr: " + ((LegacyPacketTypes.ErrorMessage) message).message()));
            if(minecraft.currentScreen instanceof LegacyVideoScreen)
                ((LegacyVideoScreen)minecraft.currentScreen).showError(((LegacyPacketTypes.ErrorMessage)message).message());
        }
    }

    void tick() {
        if (!helloSent) { hello(); return; }
        if (!helloConfirmed) return;
        if (ProtocolLimits.commandProbeEnabled() && !commandProbeSent && Minecraft.getMinecraft().thePlayer != null) {
            commandProbeSent = true;
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/cinemarr status");
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/cinemarr diagnostics");
        }
        long now = System.currentTimeMillis();
        long interval = mediaClockReady() ? STEADY_CLOCK_SYNC_INTERVAL_MS : STARTUP_CLOCK_SYNC_INTERVAL_MS;
        if (now - lastTimeSync >= interval) requestTimeSync();
        if (ProtocolLimits.segmentPressurePeerEnabled()) {
            if ("video:segment-pressure-start".equals(acceptanceControl.poll())) segmentPeer.start(now);
            segmentPeer.tick(now, this::sendSegmentPeer, value -> Cinemarr.LOGGER.info(value));
            return;
        }
        inspectAcceptanceVideoControl();
        LegacyVideoClientState.INSTANCE.tick(now);
        LegacyVideoRuntime.INSTANCE.tick();
    }

    private void hello() {
        if (ProtocolLimits.clientHelloSuppressed()) return;
        LegacyNetwork.sendToServer(LegacyPacketTypes.CLIENT_HELLO,
                new LegacyPacketTypes.ClientHello(ProtocolLimits.clientHelloVersion()));
        helloSent = true;
    }

    void stop() {
        segmentPeer.reset();
        clock.reset(); helloSent = false; helloConfirmed = false; commandProbeSent = false; operatorProbeSent = false; lastTimeSync = 0L;
        acceptanceVideoController = 0L; acceptanceVideoTuneSent = false;
        acceptanceVideoResetSent = false;
        acceptanceVideoLibrariesRequested = false; acceptanceVideoBrowseRequested = false; acceptanceVideoPlaySent = false;
        acceptanceVideoLibraryId = ""; acceptanceVideoLastItemKey = ""; acceptanceControl.reset();
        LegacyVideoClientState.INSTANCE.reset(); LegacyVideoRuntime.INSTANCE.reset();
    }

    void worldUnloaded() {
        segmentPeer.reset();
        // Dimension changes keep the socket/hello/clock, but never the old
        // world's televisions, decoder work, textures or positional sources.
        acceptanceVideoController = 0L;
        LegacyVideoClientState.INSTANCE.reset();
        LegacyVideoRuntime.INSTANCE.reset();
    }

    long serverEpoch(long localEpochMs) { return clock.initialized() ? clock.toServerTime(localEpochMs) : localEpochMs; }
    boolean mediaClockReady() { return clock.qualityReady(STARTUP_CLOCK_MIN_SAMPLES, STARTUP_CLOCK_MAX_ROUND_TRIP_MS); }

    void operatorCommandProbe() {
        if (!ProtocolLimits.commandProbeEnabled() || operatorProbeSent || Minecraft.getMinecraft().thePlayer == null) return;
        operatorProbeSent = true;
        Minecraft.getMinecraft().thePlayer.sendChatMessage("/cinemarr diagnostics");
    }

    private void requestTimeSync() {
        if (Minecraft.getMinecraft().getNetHandler() == null) return;
        long now = System.currentTimeMillis();
        LegacyNetwork.sendToServer(LegacyPacketTypes.TIME_SYNC_REQUEST,
                new LegacyPacketTypes.TimeSyncRequest(timeNonce.incrementAndGet(), now));
        lastTimeSync = now;
    }

    private void sendSegmentPeer(stonytark.cinemarr.core.protocol.CinemarrMessage message) {
        if (message instanceof VideoPackets.SegmentRequest) LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SEGMENT_REQUEST, (VideoPackets.SegmentRequest) message);
        else if (message instanceof VideoPackets.SegmentAcknowledgement) LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SEGMENT_ACKNOWLEDGEMENT, (VideoPackets.SegmentAcknowledgement) message);
    }

    private void acceptVideoProbe(LegacyPacketTypes.Type<?> type, Object payload) {
        if (!ProtocolLimits.videoProbeEnabled()) return;
        if (type == LegacyPacketTypes.VIDEO_SESSION_STATE) {
            VideoPackets.SessionState state = (VideoPackets.SessionState) payload;
            if (state.item() != null) acceptanceVideoLastItemKey = state.item().key();
            Cinemarr.LOGGER.info("Acceptance video session: controller={} session={} generation={} status={} item={} positionMs={} canControl={} streams={} audio={} subtitle={} durationMs={} message={}",
                    state.controllerPos(), state.sessionId(), state.generation(), state.status(),
                    state.item() == null ? "" : state.item().key(), state.positionMs(), state.canControl(),
                    state.streams().size(), state.selectedAudioStreamId(), state.selectedSubtitleStreamId(), state.durationMs(), state.message());
            acceptanceVideoController = state.controllerPos();
            if (!ProtocolLimits.videoProbeLeader() || !state.canControl()) return;
            if (!acceptanceVideoTuneSent) {
                acceptanceVideoTuneSent = true;
                LegacyVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE,
                        state.controllerPos(), "", "", "cinemarr-acceptance", PresentationMode.FIT,
                        state.timelineGeneration(), 0L, -1, -1));
            } else if (state.item() != null && !acceptanceVideoResetSent) {
                acceptanceVideoResetSent = true;
                LegacyVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.STOP,
                        state.controllerPos(), "", "", "", PresentationMode.FIT,
                        state.timelineGeneration(), 0L, -1, -1));
            } else if (state.item() == null && !acceptanceVideoLibrariesRequested) {
                acceptanceVideoResetSent = true;
                acceptanceVideoLibrariesRequested = true; LegacyVideoClientState.INSTANCE.requestLibraries();
            }
        } else if (type == LegacyPacketTypes.VIDEO_SESSION_QUEUE) {
            VideoPackets.SessionQueue queue = (VideoPackets.SessionQueue) payload;
            Cinemarr.LOGGER.info("Acceptance video queue: session={} generation={} entries={} firstItem={}",
                    queue.sessionId(), queue.generation(), queue.entries().size(),
                    queue.entries().isEmpty() ? "" : queue.entries().get(0).item().key());
        } else if (type == LegacyPacketTypes.VIDEO_LIBRARY_LIST) {
            VideoPackets.LibraryList value = (VideoPackets.LibraryList) payload;
            if (ProtocolLimits.videoProbeLeader() && acceptanceVideoController != 0L
                    && !acceptanceVideoBrowseRequested && !value.libraries().isEmpty()) {
                acceptanceVideoBrowseRequested = true;
                LegacyVideoClientState.INSTANCE.browse(value.libraries().get(0).id(), "", "", 0);
            }
        } else if (type == LegacyPacketTypes.VIDEO_BROWSE_RESULTS) {
            VideoPackets.BrowseResults value = (VideoPackets.BrowseResults) payload;
            if (ProtocolLimits.browsePressureProbeEnabled()) Cinemarr.LOGGER.info("Acceptance browse result: query={}", value.query());
            acceptanceVideoLibraryId = value.libraryId();
            if (ProtocolLimits.videoProbeLeader() && !acceptanceVideoPlaySent
                    && acceptanceVideoController != 0L && !value.items().isEmpty()) {
                VideoPackets.SessionState state = LegacyVideoClientState.INSTANCE.session(acceptanceVideoController);
                if (state == null) return;
                acceptanceVideoPlaySent = true;
                LegacyVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.PLAY,
                        acceptanceVideoController, value.libraryId(), value.items().get(0).key(), "",
                        PresentationMode.FIT, state.timelineGeneration(), 0L, -1, -1));
            }
        } else if (type == LegacyPacketTypes.VIDEO_MANIFEST) {
            VideoPackets.SegmentManifest value = (VideoPackets.SegmentManifest) payload;
            Cinemarr.LOGGER.info("Acceptance video manifest: session={} generation={} dimensions={}x{} segments={}",
                    value.sessionId(), value.generation(), value.width(), value.height(), value.segments().size());
        }
    }

    private void inspectAcceptanceVideoControl() {
        if (!ProtocolLimits.videoProbeEnabled()) return;
        String operation = acceptanceControl.poll();
        if (operation.length() == 0 || !operation.startsWith("video:")) return;
        VideoPackets.SessionState state = LegacyVideoClientState.INSTANCE.session(acceptanceVideoController);
        if (operation.startsWith("video:browse-pressure:") && ProtocolLimits.browsePressureProbeEnabled()) {
            VideoPackets.LibraryList libraries = LegacyVideoClientState.INSTANCE.libraries();
            if (state == null || state.canControl() || libraries.libraries().isEmpty()) return;
            String query = operation.substring("video:browse-pressure:".length());
            LegacyVideoClientState.INSTANCE.browse(libraries.libraries().get(0).id(), "", query, 0);
            Cinemarr.LOGGER.info("Acceptance browse pressure sent: query={}", query);
            return;
        }
        if ("video:open-ui".equals(operation)) {
            if (state == null) { Cinemarr.LOGGER.info("Acceptance video UI request unavailable: no session state"); return; }
            // F3+T also toggles the legacy debug overlay. Acceptance capture
            // preparation must establish a clear view, not blindly toggle F3.
            Minecraft.getMinecraft().gameSettings.showDebugInfo = false;
            Minecraft.getMinecraft().displayGuiScreen(new LegacyVideoScreen(state.controllerPos(), LegacyVideoClientState.INSTANCE));
            // Match OPEN_VIDEO_SCREEN: a cached library list alone does not
            // initialize the newly opened controller's selected library.
            LegacyVideoClientState.INSTANCE.requestLibraries();
            Cinemarr.LOGGER.info("Acceptance video UI request: controller={} canControl={}", state.controllerPos(), state.canControl());
            return;
        }
        if (state == null || (state.item() == null && !("video:replay".equals(operation) && !acceptanceVideoLastItemKey.isEmpty()))) {
            Cinemarr.LOGGER.info("Acceptance video action unavailable: operation={} state={}", operation, state == null ? "missing" : state.status());
            return;
        }
        if (!ProtocolLimits.videoProbeLeader() || !state.canControl()) {
            Cinemarr.LOGGER.info("Acceptance video control denied locally: operation={} canControl={}", operation, state.canControl());
            return;
        }
        VideoPackets.SessionAction action;
        long seek = LegacyVideoPlayback.authoritativePositionMs(state,serverEpoch(System.currentTimeMillis()));
        int audio = state.selectedAudioStreamId(), subtitle = state.selectedSubtitleStreamId();
        if ("video:pause".equals(operation)) action = VideoPackets.SessionAction.PAUSE;
        else if ("video:resume".equals(operation)) action = VideoPackets.SessionAction.RESUME;
        else if ("video:queue-current".equals(operation)) action = VideoPackets.SessionAction.QUEUE;
        else if ("video:stop".equals(operation)) action = VideoPackets.SessionAction.STOP;
        else if ("video:replay".equals(operation)) { action = VideoPackets.SessionAction.PLAY; seek = 0; }
        else if ("video:seek-near-end".equals(operation)) {
            action = VideoPackets.SessionAction.SEEK;
            seek = Math.max(0L, state.durationMs() - 12_000L);
        }
        else if ("video:seek-forward".equals(operation)) {
            action = VideoPackets.SessionAction.SEEK;
            long maximum = state.durationMs() > 5_000L ? state.durationMs() - 5_000L : state.durationMs();
            seek = Math.max(0L, Math.min(maximum, seek + 15_000L));
        } else if ("video:cycle-stream".equals(operation)) {
            action = VideoPackets.SessionAction.SET_STREAMS;
            boolean changed = false;
            for (VideoStreamOption option : state.streams()) if (option.kind() == VideoStreamOption.Kind.AUDIO && option.id() != audio) {
                audio = option.id(); changed = true; break;
            }
            if (!changed) for (VideoStreamOption option : state.streams()) if (option.kind() == VideoStreamOption.Kind.SUBTITLE && option.id() != subtitle) {
                subtitle = option.id(); changed = true; break;
            }
            if (!changed) {
                Cinemarr.LOGGER.info("Acceptance video stream selection unavailable: streams={} audio={} subtitle={}", state.streams().size(), audio, subtitle);
                return;
            }
        } else {
            Cinemarr.LOGGER.info("Acceptance video action unavailable: operation={} state=unsupported", operation);
            return;
        }
        Cinemarr.LOGGER.info("Acceptance video action: {} generation={} positionMs={} targetMs={} audio={} subtitle={}",
                action, state.timelineGeneration(), state.positionMs(), seek, audio, subtitle);
        LegacyVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(action, state.controllerPos(),
                acceptanceVideoLibraryId, state.item() == null ? acceptanceVideoLastItemKey : state.item().key(), "", state.presentationMode(), state.timelineGeneration(), seek, audio, subtitle));
    }

    private LegacyClientState() {}
}
