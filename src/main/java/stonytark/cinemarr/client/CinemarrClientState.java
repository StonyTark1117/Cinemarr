package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.ClockSynchronizer;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.protocol.AcceptanceControlFile;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.concurrent.atomic.AtomicLong;

/** Client connection clock and television payload dispatcher. */
public final class CinemarrClientState {
    private static final int STARTUP_CLOCK_MIN_SAMPLES = 8;
    private static final long STARTUP_CLOCK_MAX_ROUND_TRIP_MS = 150;
    private static final long STARTUP_CLOCK_SYNC_INTERVAL_MS = 250;
    private static final long STEADY_CLOCK_SYNC_INTERVAL_MS = 10_000;
    public static final CinemarrClientState INSTANCE = new CinemarrClientState();

    private final ClockSynchronizer clock = new ClockSynchronizer();
    private final AcceptanceControlFile acceptanceControl = new AcceptanceControlFile();
    private final AtomicLong timeNonce = new AtomicLong();
    private long lastTimeSync;
    private long acceptanceVideoController;
    private boolean acceptanceVideoTuneSent;
    private boolean acceptanceVideoResetSent;
    private boolean acceptanceVideoLibrariesRequested;
    private boolean acceptanceVideoBrowseRequested;
    private boolean acceptanceVideoPlaySent;
    private String acceptanceVideoLibraryId = "";
    private Boolean acceptanceCommandOperator;
    private boolean acceptanceDiagnosticsSent;

    public void accept(CinemarrMessage payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (CinemarrVideoClientState.INSTANCE.accept(payload)) {
            acceptVideoProbe(payload);
            refreshScreen(minecraft);
            return;
        }
        if (payload instanceof VideoPayloads.OpenVideoScreen value) {
            CinemarrVideoClientState.INSTANCE.requestLibraries();
            CinemarrVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE,
                    value.controllerPos(), "", "", "", PresentationMode.FIT, 0, 0, -1, -1));
            CinemarrClientUi.openVideoScreen(value.controllerPos());
        } else if (payload instanceof CinemarrPayloads.ServerHello value) {
            if ((!value.valid() || value.protocolVersion() != ProtocolLimits.clientHelloVersion()) && minecraft.getConnection() != null) {
                minecraft.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal(
                        "Cinemarr protocol mismatch: server requires version " + value.protocolVersion()));
            } else requestTimeSync();
        } else if (payload instanceof CinemarrPayloads.TimeSyncResponse value) {
            ClockSynchronizer.Sample sample = clock.accept(value.clientSentEpochMs(), value.serverEpochMs(), System.currentTimeMillis());
            if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance media clock: sample={} roundTripMs={} rawOffsetMs={} filteredOffsetMs={} bestRoundTripMs={}",
                    clock.sampleCount(), sample.roundTripMs(), sample.rawOffsetMs(), sample.filteredOffsetMs(), clock.bestRoundTripMs());
        } else if (payload instanceof CinemarrPayloads.ErrorMessage value && minecraft.player != null) {
            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Cinemarr: " + value.message()));
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long interval = mediaClockReady() ? STEADY_CLOCK_SYNC_INTERVAL_MS : STARTUP_CLOCK_SYNC_INTERVAL_MS;
        if (now - lastTimeSync >= interval) requestTimeSync();
        inspectAcceptanceVideoControl();
        inspectAcceptanceCommands();
    }

    public void hello() {
        CinemarrNetwork.sendToServer(new CinemarrPayloads.ClientHello(ProtocolLimits.clientHelloVersion()));
        requestTimeSync();
    }

    public long serverToLocalEpoch(long serverEpochMs) { return clock.initialized() ? clock.toLocalTime(serverEpochMs) : serverEpochMs; }
    public boolean mediaClockReady() {
        return clock.qualityReady(STARTUP_CLOCK_MIN_SAMPLES, STARTUP_CLOCK_MAX_ROUND_TRIP_MS);
    }

    public void stop() {
        clock.reset(); lastTimeSync = 0; CinemarrVideoClientState.INSTANCE.reset();
        acceptanceVideoController = 0; acceptanceVideoTuneSent = false;
        acceptanceVideoResetSent = false;
        acceptanceVideoLibrariesRequested = false; acceptanceVideoBrowseRequested = false; acceptanceVideoPlaySent = false;
        acceptanceVideoLibraryId = ""; acceptanceControl.reset();
        acceptanceCommandOperator = null;
        acceptanceDiagnosticsSent = false;
    }

    private void requestTimeSync() {
        if (Minecraft.getInstance().getConnection() == null) return;
        long now = System.currentTimeMillis();
        CinemarrNetwork.sendToServer(new CinemarrPayloads.TimeSyncRequest(timeNonce.incrementAndGet(), now));
        lastTimeSync = now;
    }

    private void acceptVideoProbe(CinemarrMessage payload) {
        if (!ProtocolLimits.videoProbeEnabled()) return;
        if (payload instanceof VideoPayloads.SessionState value) {
            VideoPackets.SessionState state = value.value();
            Cinemarr.LOGGER.info("Acceptance video session: controller={} session={} generation={} status={} item={} positionMs={} canControl={} streams={} audio={} subtitle={} message={}",
                    state.controllerPos(), state.sessionId(), state.generation(), state.status(),
                    state.item() == null ? "" : state.item().key(), state.positionMs(), state.canControl(),
                    state.streams().size(), state.selectedAudioStreamId(), state.selectedSubtitleStreamId(), state.message());
            acceptanceVideoController = state.controllerPos();
            if (!ProtocolLimits.videoProbeLeader() || !state.canControl()) return;
            if (!acceptanceVideoTuneSent) {
                acceptanceVideoTuneSent = true;
                CinemarrVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE,
                        state.controllerPos(), "", "", "cinemarr-acceptance", PresentationMode.FIT,
                        state.generation(), 0, -1, -1));
            } else if (state.item() != null && !acceptanceVideoResetSent) {
                acceptanceVideoResetSent = true;
                CinemarrVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.STOP,
                        state.controllerPos(), "", "", "", PresentationMode.FIT,
                        state.generation(), 0, -1, -1));
            } else if (state.item() == null && !acceptanceVideoLibrariesRequested) {
                acceptanceVideoResetSent = true;
                acceptanceVideoLibrariesRequested = true; CinemarrVideoClientState.INSTANCE.requestLibraries();
            }
        } else if (payload instanceof VideoPayloads.LibraryList value) {
            Cinemarr.LOGGER.info("Acceptance video libraries: count={}", value.value().libraries().size());
            if (ProtocolLimits.videoProbeLeader() && acceptanceVideoController != 0
                    && !acceptanceVideoBrowseRequested && !value.value().libraries().isEmpty()) {
                acceptanceVideoBrowseRequested = true;
                CinemarrVideoClientState.INSTANCE.browse(value.value().libraries().get(0).id(), "", "", 0);
            }
        } else if (payload instanceof VideoPayloads.BrowseResults value) {
            Cinemarr.LOGGER.info("Acceptance video browse: library={} items={}", value.value().libraryId(), value.value().items().size());
            acceptanceVideoLibraryId = value.value().libraryId();
            if (ProtocolLimits.videoProbeLeader() && !acceptanceVideoPlaySent
                    && acceptanceVideoController != 0 && !value.value().items().isEmpty()) {
                VideoPackets.SessionState state = CinemarrVideoClientState.INSTANCE.session(acceptanceVideoController);
                if (state == null) return;
                acceptanceVideoPlaySent = true;
                CinemarrVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.PLAY,
                        acceptanceVideoController, value.value().libraryId(), value.value().items().get(0).key(),
                        "", PresentationMode.FIT, state.generation(), 0, -1, -1));
            }
        } else if (payload instanceof VideoPayloads.SegmentManifest value) {
            Cinemarr.LOGGER.info("Acceptance video manifest: session={} generation={} dimensions={}x{} segments={}",
                    value.value().sessionId(), value.value().generation(), value.value().width(),
                    value.value().height(), value.value().segments().size());
        }
    }

    private void inspectAcceptanceVideoControl() {
        if (!ProtocolLimits.videoProbeEnabled()) return;
        String operation = acceptanceControl.poll();
        if (operation.isEmpty() || !operation.startsWith("video:")) return;
        VideoPackets.SessionState state = CinemarrVideoClientState.INSTANCE.session(acceptanceVideoController);
        if ("video:open-ui".equals(operation)) {
            if (state == null) { Cinemarr.LOGGER.info("Acceptance video UI request unavailable: no session state"); return; }
            CinemarrClientUi.openVideoScreen(state.controllerPos());
            Cinemarr.LOGGER.info("Acceptance video UI request: controller={} canControl={}", state.controllerPos(), state.canControl());
            return;
        }
        if (state == null || state.item() == null) {
            Cinemarr.LOGGER.info("Acceptance video action unavailable: operation={} state={}", operation, state == null ? "missing" : state.status());
            return;
        }
        if (!ProtocolLimits.videoProbeLeader() || !state.canControl()) {
            Cinemarr.LOGGER.info("Acceptance video control denied locally: operation={} canControl={}", operation, state.canControl());
            return;
        }
        VideoPackets.SessionAction action;
        long seek = state.positionMs();
        int audio = state.selectedAudioStreamId(), subtitle = state.selectedSubtitleStreamId();
        if ("video:pause".equals(operation)) action = VideoPackets.SessionAction.PAUSE;
        else if ("video:resume".equals(operation)) action = VideoPackets.SessionAction.RESUME;
        else if ("video:seek-forward".equals(operation)) {
            action = VideoPackets.SessionAction.SEEK;
            long maximum = state.durationMs() > 5_000 ? state.durationMs() - 5_000 : state.durationMs();
            seek = Math.max(0, Math.min(maximum, state.positionMs() + 15_000));
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
                action, state.generation(), state.positionMs(), seek, audio, subtitle);
        CinemarrVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(action, state.controllerPos(),
                acceptanceVideoLibraryId, state.item().key(), "", state.presentationMode(), state.generation(), seek, audio, subtitle));
    }

    private void inspectAcceptanceCommands() {
        if (!ProtocolLimits.commandProbeEnabled()) return;
        net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        com.mojang.brigadier.tree.CommandNode<?> cinemarr = connection.getCommands().getRoot().getChild("cinemarr");
        if (cinemarr == null || cinemarr.getChild("status") == null) return;
        boolean operator = cinemarr.getChild("diagnostics") != null && cinemarr.getChild("retry") != null;
        if (acceptanceCommandOperator != null && acceptanceCommandOperator.booleanValue() == operator) return;
        acceptanceCommandOperator = operator;
        Cinemarr.LOGGER.info("Acceptance command permissions: {} public=true operator={}",
                operator ? "operator" : "non-operator", operator);
        if (operator && !acceptanceDiagnosticsSent && Minecraft.getInstance().player != null) {
            acceptanceDiagnosticsSent = true;
            Minecraft.getInstance().player.connection.sendCommand("cinemarr diagnostics");
        }
    }

    private static void refreshScreen(Minecraft minecraft) {
        CinemarrClientUi.refreshVideoScreen();
    }

    private CinemarrClientState() {}
}
