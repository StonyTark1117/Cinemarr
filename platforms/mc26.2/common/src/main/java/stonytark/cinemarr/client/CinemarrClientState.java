package stonytark.cinemarr.client;

import com.mojang.brigadier.tree.CommandNode;
import stonytark.cinemarr.core.client.ClockSynchronizer;
import net.minecraft.client.Minecraft;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.AcceptanceControlFile;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.PresentationMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class CinemarrClientState {
    private static final int BROWSE_PAGE_SIZE = 20;
    private static final int STARTUP_CLOCK_SAMPLES = 8;
    private static final long STARTUP_CLOCK_SYNC_INTERVAL_MS = 250;
    private static final long STEADY_CLOCK_SYNC_INTERVAL_MS = 10_000;
    public static final CinemarrClientState INSTANCE = new CinemarrClientState();
    private final ClockSynchronizer clock = new ClockSynchronizer();
    private final CinemarrAudioPlayer audio = new CinemarrAudioPlayer(clock);
    private final AtomicLong timeNonce = new AtomicLong();
    private final AcceptanceControlFile acceptanceControl = new AcceptanceControlFile();
    private CinemarrPayloads.PlaybackState playback = new CinemarrPayloads.PlaybackState(CinemarrPayloads.PlaybackStatus.IDLE, "", "", "", true, 0, 0, 0, false, List.of());
    private CinemarrPayloads.StationState station = new CinemarrPayloads.StationState(CinemarrPayloads.StationType.NONE, false, false, 0,
            CinemarrPayloads.SonicCapability.CHECKING, "Checking Plex sonic capability", "", List.of(), List.of());
    private CinemarrPayloads.AdventurePreview adventurePreview = new CinemarrPayloads.AdventurePreview(0, "", List.of());
    private CinemarrPayloads.BrowseResults browse = new CinemarrPayloads.BrowseResults(CinemarrPayloads.BrowseKind.SEARCH, "", 0, false, List.of());
    private long lastTimeSync;
    private String notice = "";
    private boolean nonOperatorCommandsVerified;
    private boolean operatorCommandsVerified;
    private boolean acceptanceAudioQueued;
    private AudioPlaybackState lastAcceptanceAudioState;

    public void accept(CinemarrMessage payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (CinemarrVideoClientState.INSTANCE.accept(payload)) {
            refreshScreen(minecraft);
            return;
        }
        if (payload instanceof VideoPayloads.OpenVideoScreen value) {
            CinemarrVideoClientState.INSTANCE.requestLibraries();
            CinemarrVideoClientState.INSTANCE.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE,
                    value.controllerPos(), "", "", "", PresentationMode.FIT, 0, 0, -1, -1));
            minecraft.setScreenAndShow(new CinemarrVideoScreen(value.controllerPos(), CinemarrVideoClientState.INSTANCE));
        } else if (payload instanceof CinemarrPayloads.OpenScreen) {
            minecraft.setScreenAndShow(new CinemarrScreen(this));
            CinemarrNetwork.sendToServer(new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "", 0));
        } else if (payload instanceof CinemarrPayloads.ServerHello value) {
            if (value.protocolVersion() != ProtocolLimits.clientHelloVersion() && minecraft.getConnection() != null) {
                minecraft.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal(
                        "Cinemarr protocol mismatch: server requires version " + value.protocolVersion()));
            } else {
                requestTimeSync();
                queueAcceptanceAudio();
            }
        } else if (payload instanceof CinemarrPayloads.TimeSyncResponse value) {
            clock.accept(value.clientSentEpochMs(), value.serverEpochMs(), System.currentTimeMillis());
        } else if (payload instanceof CinemarrPayloads.BrowseResults value) {
            browse = value; refreshScreen(minecraft);
        } else if (payload instanceof CinemarrPayloads.PlaybackState value) {
            playback = value;
            logAcceptancePlayback(value);
            if (value.serverEpochMs() > 0 && !clock.initialized()) clock.accept(System.currentTimeMillis(), value.serverEpochMs(), System.currentTimeMillis());
            boolean queueBrowseChanged = refreshQueueBrowse();
            if (minecraft.gui.screen() instanceof CinemarrScreen screen) {
                screen.playbackChanged();
                if (queueBrowseChanged) screen.queueChanged();
            }
        } else if (payload instanceof CinemarrPayloads.StationState value) {
            station = value;
            if (ProtocolLimits.audioProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance station state: type={} active={} autoplay={} generation={} preview={}",
                    value.stationType(), value.active(), value.autoplayEnabled(), value.generation(), value.preview().size());
            if (minecraft.gui.screen() instanceof CinemarrScreen screen) screen.stationChanged();
        } else if (payload instanceof CinemarrPayloads.AdventurePreview value) {
            adventurePreview = value;
            if (minecraft.gui.screen() instanceof CinemarrScreen screen) screen.adventurePreviewChanged();
        } else if (payload instanceof CinemarrPayloads.AudioManifest value) {
            if (ProtocolLimits.audioProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance audio manifest: session={} title={} firstChunk={} paused={}",
                    value.sessionId(), value.title(), value.firstChunk(), value.paused());
            audio.manifest(value);
        } else if (payload instanceof CinemarrPayloads.AudioChunk value) {
            audio.chunk(value);
        } else if (payload instanceof CinemarrPayloads.ErrorMessage value) {
            notice = value.message();
            if (minecraft.player != null) minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Cinemarr: " + value.message()));
            refreshScreen(minecraft);
            if (minecraft.gui.screen() instanceof CinemarrScreen screen) screen.requestFailed();
        }
    }

    public CinemarrPayloads.PlaybackState playback() { return playback; }
    public CinemarrPayloads.BrowseResults browse() { return browse; }
    public CinemarrPayloads.StationState station() { return station; }
    public CinemarrPayloads.AdventurePreview adventurePreview() { return adventurePreview; }
    public String notice() { return notice; }
    public String audioStatus() { return audio.status(); }
    public AudioPlaybackState audioState() { return audio.state(); }
    public void clearNotice() { notice = ""; }
    public void clearBrowse(CinemarrPayloads.BrowseKind kind, String query) {
        browse = new CinemarrPayloads.BrowseResults(kind, query, 0, false, List.of());
    }

    private boolean refreshQueueBrowse() {
        if (browse.kind() != CinemarrPayloads.BrowseKind.QUEUE) return false;
        return showQueuePage(browse.page());
    }
    public boolean showQueuePage(int requestedPage) {
        int page = Math.max(0, requestedPage);
        if ((long)page * BROWSE_PAGE_SIZE >= playback.queue().size() && page > 0) page = 0;
        int start = page * BROWSE_PAGE_SIZE;
        int end = Math.min(playback.queue().size(), start + BROWSE_PAGE_SIZE);
        List<CinemarrPayloads.MediaItem> items = playback.queue().subList(start, end).stream()
                .map(entry -> new CinemarrPayloads.MediaItem(CinemarrPayloads.ItemKind.TRACK, entry.key(), entry.title(), entry.artist(), entry.durationMs()))
                .toList();
        CinemarrPayloads.BrowseResults updated = new CinemarrPayloads.BrowseResults(CinemarrPayloads.BrowseKind.QUEUE, "", page, end < playback.queue().size(), items);
        if (browse.equals(updated)) return false;
        browse = updated;
        return true;
    }
    public void tick() {
        probeCommandPermissions();
        runAcceptanceControl();
        logAcceptanceAudioState();
        long now = System.currentTimeMillis();
        long syncInterval = clock.sampleCount() < STARTUP_CLOCK_SAMPLES
                ? STARTUP_CLOCK_SYNC_INTERVAL_MS : STEADY_CLOCK_SYNC_INTERVAL_MS;
        if (now - lastTimeSync >= syncInterval) requestTimeSync();
        audio.tick();
    }
    public void hello() { CinemarrNetwork.sendToServer(new CinemarrPayloads.ClientHello(ProtocolLimits.clientHelloVersion())); requestTimeSync(); }
    /** Converts a server epoch using the filtered synchronization estimate. */
    public long serverToLocalEpoch(long serverEpochMs) {
        return clock.initialized() ? clock.toLocalTime(serverEpochMs) : serverEpochMs;
    }
    public boolean mediaClockReady() { return clock.sampleCount() >= STARTUP_CLOCK_SAMPLES; }
    public void ensureAudio() { audio.ensureStarted(); }
    public void listeningChanged() { audio.listeningChanged(); }
    public void retryAudio() { audio.retry(); refreshScreen(Minecraft.getInstance()); }
    public void audioEngineReloaded() { audio.audioEngineReloaded(); }
    public void stop() {
        audio.stop(); clock.reset(); notice = ""; lastTimeSync = 0;
        CinemarrVideoClientState.INSTANCE.reset();
        nonOperatorCommandsVerified = false; operatorCommandsVerified = false;
        acceptanceAudioQueued = false; lastAcceptanceAudioState = null;
        acceptanceControl.reset();
        playback = new CinemarrPayloads.PlaybackState(CinemarrPayloads.PlaybackStatus.IDLE, "", "", "", true, 0, 0, 0, false, List.of());
        station = new CinemarrPayloads.StationState(CinemarrPayloads.StationType.NONE, false, false, 0,
                CinemarrPayloads.SonicCapability.CHECKING, "Checking Plex sonic capability", "", List.of(), List.of());
        adventurePreview = new CinemarrPayloads.AdventurePreview(0, "", List.of());
    }

    private void requestTimeSync() {
        if (Minecraft.getInstance().getConnection() == null) return;
        long now = System.currentTimeMillis();
        CinemarrNetwork.sendToServer(new CinemarrPayloads.TimeSyncRequest(timeNonce.incrementAndGet(), now));
        lastTimeSync = now;
    }
    private void probeCommandPermissions() {
        if (!ProtocolLimits.commandProbeEnabled()) return;
        net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        CommandNode<?> root = connection.getCommands().getRoot().getChild("cinemarr");
        if (root == null || root.getChild("status") == null) return;
        boolean operatorVisible = root.getChild("diagnostics") != null;
        if (!nonOperatorCommandsVerified && !operatorVisible) {
            nonOperatorCommandsVerified = true;
            Cinemarr.LOGGER.info("Acceptance command permissions: non-operator public=true operator=false");
        } else if (nonOperatorCommandsVerified && !operatorCommandsVerified && operatorVisible) {
            operatorCommandsVerified = true;
            Cinemarr.LOGGER.info("Acceptance command permissions: operator public=true operator=true");
            connection.sendCommand("cinemarr diagnostics");
            Cinemarr.LOGGER.info("Acceptance client issued: /cinemarr diagnostics");
        }
    }
    private void queueAcceptanceAudio() {
        if (!ProtocolLimits.audioProbeLeader() || acceptanceAudioQueued) return;
        acceptanceAudioQueued = true;
        CinemarrNetwork.sendToServer(new CinemarrPayloads.QueueRequest(CinemarrPayloads.ItemKind.TRACK, "42"));
        Cinemarr.LOGGER.info("Acceptance audio leader queued Plex track 42");
    }
    private void logAcceptanceAudioState() {
        if (!ProtocolLimits.audioProbeEnabled()) return;
        AudioPlaybackState state = audio.state();
        if (state == lastAcceptanceAudioState) return;
        lastAcceptanceAudioState = state;
        Cinemarr.LOGGER.info("Acceptance audio state: {}", state);
    }
    private void logAcceptancePlayback(CinemarrPayloads.PlaybackState value) {
        if (!ProtocolLimits.audioProbeEnabled()) return;
        StringBuilder queue = new StringBuilder();
        for (CinemarrPayloads.QueueEntry entry : value.queue()) {
            if (queue.length() != 0) queue.append(',');
            queue.append(entry.key());
        }
        Cinemarr.LOGGER.info("Acceptance playback state: status={} paused={} title={} origin={} queue={}",
                value.status(), value.paused(), value.title(), value.origin(), queue);
    }
    private void runAcceptanceControl() {
        String command = acceptanceControl.poll();
        if (command.isEmpty()) return;
        try {
            if (command.startsWith("queue:")) {
                CinemarrNetwork.sendToServer(new CinemarrPayloads.QueueRequest(
                        CinemarrPayloads.ItemKind.TRACK, command.substring("queue:".length())));
            } else if (command.startsWith("control:")) {
                String[] parts = command.split(":", -1);
                int index = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
                String expectedKey = parts.length > 3 ? parts[3] : "";
                CinemarrNetwork.sendToServer(new CinemarrPayloads.ControlRequest(
                        CinemarrPayloads.ControlAction.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT)), index, expectedKey));
            } else if (command.equals("mute")) {
                CinemarrSettings.enabled(false); listeningChanged();
            } else if (command.equals("unmute")) {
                CinemarrSettings.enabled(true); listeningChanged();
            } else if (command.startsWith("volume:")) {
                CinemarrSettings.volume(Double.parseDouble(command.substring("volume:".length())));
            } else if (command.equals("station:library-shuffle")) {
                CinemarrNetwork.sendToServer(new CinemarrPayloads.StationRequest(
                        CinemarrPayloads.StationAction.START, CinemarrPayloads.StationType.LIBRARY_SHUFFLE,
                        false, station.generation(), List.of()));
            } else if (command.startsWith("adventure:")) {
                String[] keys = command.split(":", -1);
                if (keys.length != 3) throw new IllegalArgumentException("Adventure needs two keys");
                CinemarrNetwork.sendToServer(new CinemarrPayloads.StationRequest(
                        CinemarrPayloads.StationAction.START_NOW, CinemarrPayloads.StationType.SONIC_ADVENTURE,
                        false, station.generation(), List.of(
                        new CinemarrPayloads.StationSeed(CinemarrPayloads.ItemKind.TRACK, keys[1], "Gate Track " + keys[1], "Gate Artist"),
                        new CinemarrPayloads.StationSeed(CinemarrPayloads.ItemKind.TRACK, keys[2], "Gate Track " + keys[2], "Gate Artist"))));
            } else if (command.equals("reload")) {
                Minecraft.getInstance().reloadResourcePacks().whenComplete((unused, error) ->
                        Cinemarr.LOGGER.info("Acceptance resource reload complete: success={}", error == null));
            } else if (command.equals("fault:underrun")) {
                audio.acceptanceUnderrun();
            } else if (command.equals("fault:drift")) {
                audio.acceptanceClockDrift();
            } else if (command.equals("fault:exhaust-retries")) {
                audio.acceptanceExhaustRecovery();
            } else if (command.equals("retry")) {
                audio.retry();
            } else {
                throw new IllegalArgumentException("Unknown acceptance operation");
            }
            Cinemarr.LOGGER.info("Acceptance control applied: {}", command);
        } catch (RuntimeException error) {
            Cinemarr.LOGGER.error("Acceptance control failed: {}", command, error);
        }
    }
    private static void refreshScreen(Minecraft minecraft) {
        if (minecraft.gui.screen() instanceof CinemarrScreen screen) screen.resultsChanged();
        else if(minecraft.gui.screen() instanceof CinemarrVideoScreen screen)screen.stateChanged();
    }
    private CinemarrClientState() {}
}
