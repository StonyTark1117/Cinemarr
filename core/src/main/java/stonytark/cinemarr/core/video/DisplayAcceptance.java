package stonytark.cinemarr.core.video;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;

/** Shared opt-in acceptance vocabulary. Commands still pass ordinary server validation. */
public final class DisplayAcceptance {
    /** Keep existing acceptance TVs intact when clearing sightlines after a join. */
    public static boolean sceneTvBlock(int x, int y, int z) {
        return z == 2 && y >= 110 && y <= 113 && (x >= -7 && x <= -4 || x >= 4 && x <= 7)
                || z == 3 && y == 110 && (x == -7 || x == 4);
    }
    public static String describe(VideoPackets.SessionState state) {
        TvDisplaySettings display = state.displaySettings();
        return "controller=" + state.controllerPos() + " television=" + state.televisionId()
                + " timeline=" + state.timelineId() + " timelineGeneration=" + state.timelineGeneration()
                + " stream=" + state.sessionId() + " streamGeneration=" + state.generation()
                + " status=" + state.status() + " owner=" + state.canControl() + " origin=" + display.origin()
                + " revision=" + display.revision() + " layout=" + display.layout() + " mapping=" + display.mapping()
                + " requested=" + display.resolution() + " effective=" + state.effectiveWidth() + "x" + state.effectiveHeight()
                + " screen=" + state.screenWidth() + "x" + state.screenHeight()
                + " streamState=" + streamState(state);
    }
    private static String streamState(VideoPackets.SessionState state) {
        String message = state.message();
        if ("Waiting for stream capacity".equals(message)) return "WAITING";
        if ("Preparing TV stream".equals(message)) return "PREPARING";
        if (message.startsWith("Unable to prepare TV stream")) return "FAILED";
        return state.status() == VideoPackets.SessionStatus.PLAYING ? "READY" : state.status().name();
    }
    public static boolean primary(VideoPackets.SessionState state) {
        return !ProtocolLimits.displayProbeEnabled() || state.displaySettings().origin() == TvDisplaySettings.Origin.QUICK;
    }
    public static VideoPackets.SessionState custom(Collection<VideoPackets.SessionState> televisions, int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("Invalid acceptance TV index");
        List<VideoPackets.SessionState> customs = new ArrayList<VideoPackets.SessionState>();
        for (VideoPackets.SessionState state : televisions)
            if (state.displaySettings().origin() == TvDisplaySettings.Origin.CUSTOM) customs.add(state);
        customs.sort(Comparator.comparingLong(VideoPackets.SessionState::controllerPos));
        if (customs.size() != 2) throw new IllegalStateException("Expected two custom acceptance TVs");
        return customs.get(index);
    }
    public static VideoPackets.SessionCommand command(Collection<VideoPackets.SessionState> televisions, String operation) {
        if (!ProtocolLimits.displayProbeEnabled() || !operation.startsWith("video:display:")) return null;
        String[] parts = operation.split(":");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid display probe command");
        VideoPackets.SessionState state = custom(televisions, Integer.parseInt(parts[2]));
        if ("tune-idle".equals(parts[3]) || "tune-party".equals(parts[3])) {
            String party = "tune-party".equals(parts[3]) ? "cinemarr-acceptance" : "cinemarr-acceptance-idle-" + parts[2];
            return new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE, state.controllerPos(), "", "", party,
                    state.presentationMode(), state.timelineGeneration(), 0, -1, -1);
        }
        TvDisplaySettings old = state.displaySettings();
        PresentationMode layout = old.layout(); PixelMapping mapping = old.mapping(); ResolutionChoice quality = old.resolution();
        if ("quality".equals(parts[3])) quality = ResolutionChoice.preset("240p");
        else if (parts[3].startsWith("quality-")) quality = ResolutionChoice.preset(parts[3].substring("quality-".length()));
        else if ("mapping".equals(parts[3])) mapping = mapping == PixelMapping.DETAILED ? PixelMapping.ONE_PIXEL_PER_BLOCK : PixelMapping.DETAILED;
        else if ("layout".equals(parts[3])) layout = PresentationMode.values()[(layout.ordinal() + 1) % PresentationMode.values().length];
        else throw new IllegalArgumentException("Unsupported display probe action");
        return new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_DISPLAY, state.controllerPos(), "", "", "",
                state.presentationMode(), state.timelineGeneration(), 0, state.selectedAudioStreamId(), state.selectedSubtitleStreamId())
                .withDisplay(new TvDisplaySettings(old.origin(), layout, mapping, quality, old.revision()));
    }
    private DisplayAcceptance() {}
}
