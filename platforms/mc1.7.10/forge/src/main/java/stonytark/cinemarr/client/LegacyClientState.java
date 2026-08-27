package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.ClockSynchronizer;
import stonytark.cinemarr.core.model.StationModels;
import stonytark.cinemarr.core.protocol.ControlPackets;
import stonytark.cinemarr.core.protocol.AcceptanceControlFile;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.StatePackets;
import stonytark.cinemarr.core.protocol.TransportPackets;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;
import stonytark.cinemarr.core.platform.CinemarrSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class LegacyClientState implements LegacyNetwork.ClientListener {
    static final LegacyClientState INSTANCE = new LegacyClientState();
    private static final int PAGE_SIZE = 20;
    private final ClockSynchronizer clock = new ClockSynchronizer();
    private final LegacyAudioPlayer audio = new LegacyAudioPlayer(clock);
    private final AtomicLong timeNonce = new AtomicLong();
    private final AcceptanceControlFile acceptanceControl = new AcceptanceControlFile();
    private StatePackets.PlaybackState playback = emptyPlayback();
    private StatePackets.StationState station = emptyStation();
    private StatePackets.AdventurePreview adventure = new StatePackets.AdventurePreview(
            0L, "", Collections.<StatePackets.QueueEntry>emptyList());
    private ControlPackets.BrowseResults browse = new ControlPackets.BrowseResults(
            ControlPackets.BrowseKind.SEARCH, "", 0, false, Collections.<StationModels.MediaItem>emptyList());
    private boolean helloSent;
    private boolean commandProbeSent;
    private boolean operatorProbeSent;
    private boolean acceptanceAudioQueued;
    private String lastAcceptanceAudioState = "";
    private long lastTimeSync;
    private String notice = "";

    @Override public void accept(LegacyPacketTypes.Type<?> type, Object message) {
        if (LegacyVideoClientState.INSTANCE.accept(type, message)) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (type == LegacyPacketTypes.OPEN_SCREEN) {
            minecraft.displayGuiScreen(new LegacyScreen(this));
        } else if (type == LegacyPacketTypes.SERVER_HELLO) {
            ControlPackets.ServerHello hello = (ControlPackets.ServerHello) message;
            if (hello.protocolVersion() != ProtocolLimits.clientHelloVersion() && minecraft.getNetHandler() != null) {
                minecraft.getNetHandler().getNetworkManager().closeChannel(
                        new ChatComponentText("Cinemarr protocol mismatch: server requires version "
                                + hello.protocolVersion()));
            } else {
                requestTimeSync();
                queueAcceptanceAudio();
            }
        } else if (type == LegacyPacketTypes.TIME_SYNC_RESPONSE) {
            ControlPackets.TimeSyncResponse response = (ControlPackets.TimeSyncResponse) message;
            clock.accept(response.clientSentEpochMs(), response.serverEpochMs(), System.currentTimeMillis());
        } else if (type == LegacyPacketTypes.BROWSE_RESULTS) {
            browse = (ControlPackets.BrowseResults) message;
            screenResultsChanged();
        } else if (type == LegacyPacketTypes.PLAYBACK_STATE) {
            playback = (StatePackets.PlaybackState) message;
            logAcceptancePlayback(playback);
            if (playback.serverEpochMs() > 0L && !clock.initialized()) {
                long now = System.currentTimeMillis();
                clock.accept(now, playback.serverEpochMs(), now);
            }
            refreshQueueBrowse(); screenChanged();
        } else if (type == LegacyPacketTypes.STATION_STATE) {
            station = (StatePackets.StationState) message;
            if (ProtocolLimits.audioProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance station state: type={} active={} autoplay={} generation={} preview={}",
                    station.stationType(), station.active(), station.autoplayEnabled(), station.generation(), station.preview().size());
            screenChanged();
        } else if (type == LegacyPacketTypes.ADVENTURE_PREVIEW) {
            adventure = (StatePackets.AdventurePreview) message; screenChanged();
        } else if (type == LegacyPacketTypes.AUDIO_MANIFEST) {
            TransportPackets.AudioManifest manifest = (TransportPackets.AudioManifest) message;
            if (ProtocolLimits.audioProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance audio manifest: session={} title={} firstChunk={} paused={}",
                    manifest.sessionId(), manifest.title(), manifest.firstChunk(), manifest.paused());
            audio.manifest(manifest);
        } else if (type == LegacyPacketTypes.AUDIO_CHUNK) {
            audio.chunk((TransportPackets.AudioChunk) message);
        } else if (type == LegacyPacketTypes.ERROR) {
            notice = ((StatePackets.ErrorMessage) message).message();
            if (minecraft.thePlayer != null) minecraft.thePlayer.addChatMessage(
                    new ChatComponentText("Cinemarr: " + notice));
            LegacyScreen screen = screen();
            if (screen != null) screen.requestFailed();
        }
    }

    void tick() {
        if (!helloSent) {
            hello();
            return;
        }
        if (ProtocolLimits.commandProbeEnabled() && !commandProbeSent
                && Minecraft.getMinecraft().thePlayer != null) {
            commandProbeSent = true;
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/cinemarr status");
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/cinemarr diagnostics");
            Cinemarr.LOGGER.info("Acceptance client issued non-operator command probes");
        }
        runAcceptanceControl();
        logAcceptanceAudioState();
        long now = System.currentTimeMillis();
        if (now - lastTimeSync >= 10_000L) requestTimeSync();
        audio.tick();
        LegacyVideoRuntime.INSTANCE.tick();
    }

    private void hello() {
        if (ProtocolLimits.clientHelloSuppressed()) return;
        LegacyNetwork.sendToServer(LegacyPacketTypes.CLIENT_HELLO, new ControlPackets.ClientHello(ProtocolLimits.clientHelloVersion()));
        helloSent = true;
    }

    void stop() {
        audio.stop(); clock.reset(); notice = ""; helloSent = false; commandProbeSent = false;
        operatorProbeSent = false; lastTimeSync = 0L;
        acceptanceAudioQueued = false; lastAcceptanceAudioState = "";
        acceptanceControl.reset();
        playback = emptyPlayback(); station = emptyStation();
        browse = new ControlPackets.BrowseResults(ControlPackets.BrowseKind.SEARCH, "", 0,
                false, Collections.<StationModels.MediaItem>emptyList());
        adventure = new StatePackets.AdventurePreview(0L, "", Collections.<StatePackets.QueueEntry>emptyList());
        LegacyVideoClientState.INSTANCE.reset();
        LegacyVideoRuntime.INSTANCE.reset();
    }

    void operatorCommandProbe() {
        if (!ProtocolLimits.commandProbeEnabled() || operatorProbeSent
                || Minecraft.getMinecraft().thePlayer == null) return;
        operatorProbeSent = true;
        Minecraft.getMinecraft().thePlayer.sendChatMessage("/cinemarr diagnostics");
        Cinemarr.LOGGER.info("Acceptance client issued: /cinemarr diagnostics");
    }

    private void queueAcceptanceAudio() {
        if (!ProtocolLimits.audioProbeLeader() || acceptanceAudioQueued) return;
        acceptanceAudioQueued = true;
        LegacyNetwork.sendToServer(LegacyPacketTypes.QUEUE_REQUEST, new ControlPackets.QueueRequest(
                StationModels.ItemKind.TRACK, "42"));
        Cinemarr.LOGGER.info("Acceptance audio leader queued Plex track 42");
    }

    private void logAcceptanceAudioState() {
        if (!ProtocolLimits.audioProbeEnabled()) return;
        String state = audio.state();
        if (state.equals(lastAcceptanceAudioState)) return;
        lastAcceptanceAudioState = state;
        Cinemarr.LOGGER.info("Acceptance audio state: {}", state);
    }

    private void logAcceptancePlayback(StatePackets.PlaybackState value) {
        if (!ProtocolLimits.audioProbeEnabled()) return;
        StringBuilder queue = new StringBuilder();
        for (StatePackets.QueueEntry entry : value.queue()) {
            if (queue.length() != 0) queue.append(',');
            queue.append(entry.key());
        }
        Cinemarr.LOGGER.info("Acceptance playback state: status={} paused={} title={} origin={} queue={}",
                value.status(), value.paused(), value.title(), value.origin(), queue);
    }

    private void runAcceptanceControl() {
        String command = acceptanceControl.poll();
        if (command.length() == 0) return;
        try {
            if (command.startsWith("queue:")) {
                LegacyNetwork.sendToServer(LegacyPacketTypes.QUEUE_REQUEST, new ControlPackets.QueueRequest(
                        StationModels.ItemKind.TRACK, command.substring("queue:".length())));
            } else if (command.startsWith("control:")) {
                String[] parts = command.split(":", -1);
                int index = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
                String expectedKey = parts.length > 3 ? parts[3] : "";
                LegacyNetwork.sendToServer(LegacyPacketTypes.CONTROL_REQUEST, new ControlPackets.ControlRequest(
                        ControlPackets.ControlAction.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT)), index, expectedKey));
            } else if ("mute".equals(command)) {
                CinemarrSettings.enabled(false); listeningChanged();
            } else if ("unmute".equals(command)) {
                CinemarrSettings.enabled(true); listeningChanged();
            } else if (command.startsWith("volume:")) {
                CinemarrSettings.volume(Double.parseDouble(command.substring("volume:".length())));
            } else if ("station:library-shuffle".equals(command)) {
                LegacyNetwork.sendToServer(LegacyPacketTypes.STATION_REQUEST, new ControlPackets.StationRequest(
                        ControlPackets.StationAction.START, StationModels.StationType.LIBRARY_SHUFFLE,
                        false, station.generation(), Collections.<StationModels.StationSeed>emptyList()));
            } else if (command.startsWith("adventure:")) {
                String[] keys = command.split(":", -1);
                if (keys.length != 3) throw new IllegalArgumentException("Adventure needs two keys");
                LegacyNetwork.sendToServer(LegacyPacketTypes.STATION_REQUEST, new ControlPackets.StationRequest(
                        ControlPackets.StationAction.START_NOW, StationModels.StationType.SONIC_ADVENTURE,
                        false, station.generation(), Arrays.asList(
                        new StationModels.StationSeed(StationModels.ItemKind.TRACK, keys[1], "Gate Track " + keys[1], "Gate Artist"),
                        new StationModels.StationSeed(StationModels.ItemKind.TRACK, keys[2], "Gate Track " + keys[2], "Gate Artist"))));
            } else if ("reload".equals(command)) {
                Minecraft.getMinecraft().refreshResources();
                Cinemarr.LOGGER.info("Acceptance resource reload complete: success=true");
            } else if ("fault:underrun".equals(command)) {
                audio.acceptanceUnderrun();
            } else if ("fault:drift".equals(command)) {
                audio.acceptanceClockDrift();
            } else if ("fault:exhaust-retries".equals(command)) {
                audio.acceptanceExhaustRecovery();
            } else if ("retry".equals(command)) {
                audio.retry();
            } else {
                throw new IllegalArgumentException("Unknown acceptance operation");
            }
            Cinemarr.LOGGER.info("Acceptance control applied: {}", command);
        } catch (RuntimeException error) {
            Cinemarr.LOGGER.error("Acceptance control failed: " + command, error);
        }
    }

    StatePackets.PlaybackState playback() { return playback; }
    StatePackets.StationState station() { return station; }
    StatePackets.AdventurePreview adventure() { return adventure; }
    ControlPackets.BrowseResults browse() { return browse; }
    String notice() { return notice; }
    void clearNotice() { notice = ""; }
    String audioStatus() { return audio.status(); }
    String audioState() { return audio.state(); }
    void listeningChanged() { audio.listeningChanged(); }
    void retryAudio() { audio.retry(); }
    void audioEngineReloaded() { audio.audioEngineReloaded(); }

    void clearBrowse(ControlPackets.BrowseKind kind, String query) {
        browse = new ControlPackets.BrowseResults(kind, query, 0, false,
                Collections.<StationModels.MediaItem>emptyList());
    }

    void showQueuePage(int requestedPage) {
        int page = Math.max(0, requestedPage);
        if ((long) page * PAGE_SIZE >= playback.queue().size() && page > 0) page = 0;
        int first = page * PAGE_SIZE;
        int end = Math.min(playback.queue().size(), first + PAGE_SIZE);
        List<StationModels.MediaItem> items = new ArrayList<StationModels.MediaItem>();
        for (StatePackets.QueueEntry entry : playback.queue().subList(first, end)) {
            items.add(new StationModels.MediaItem(StationModels.ItemKind.TRACK, entry.key(),
                    entry.title(), entry.artist(), entry.durationMs()));
        }
        browse = new ControlPackets.BrowseResults(ControlPackets.BrowseKind.QUEUE, "", page,
                end < playback.queue().size(), items);
    }

    private void refreshQueueBrowse() {
        if (browse.kind() == ControlPackets.BrowseKind.QUEUE) showQueuePage(browse.page());
    }

    private void requestTimeSync() {
        if (Minecraft.getMinecraft().getNetHandler() == null) return;
        long now = System.currentTimeMillis();
        LegacyNetwork.sendToServer(LegacyPacketTypes.TIME_SYNC_REQUEST,
                new ControlPackets.TimeSyncRequest(timeNonce.incrementAndGet(), now));
        lastTimeSync = now;
    }

    long serverEpoch(long localEpochMs) { return clock.initialized() ? clock.toServerTime(localEpochMs) : localEpochMs; }

    private LegacyScreen screen() {
        return Minecraft.getMinecraft().currentScreen instanceof LegacyScreen
                ? (LegacyScreen) Minecraft.getMinecraft().currentScreen : null;
    }
    private void screenChanged() { LegacyScreen screen = screen(); if (screen != null) screen.stateChanged(); }
    private void screenResultsChanged() { LegacyScreen screen = screen(); if (screen != null) screen.resultsChanged(); }

    private static StatePackets.PlaybackState emptyPlayback() {
        return new StatePackets.PlaybackState(StatePackets.PlaybackStatus.IDLE, "", "", "", true,
                0L, 0L, 0L, false, StatePackets.PlaybackOrigin.NONE, "",
                Collections.<StatePackets.QueueEntry>emptyList());
    }
    private static StatePackets.StationState emptyStation() {
        return new StatePackets.StationState(StationModels.StationType.NONE, false, false, 0L,
                StationModels.SonicCapability.CHECKING, "Checking Plex sonic capability", "",
                Collections.<StationModels.StationSeed>emptyList(), Collections.<StatePackets.QueueEntry>emptyList());
    }

    private LegacyClientState() {}
}
