package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.Cinemarr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Controller-specific movie/show browser and synchronized TV controls for Forge 1.7.10. */
final class LegacyVideoScreen extends GuiScreen {
    private static final int SEARCH = 1, BACK = 2, REFRESH = 3, TOGGLE_QUEUE = 4;
    private static final int PAUSE = 10, SEEK_BACK = 11, SEEK_FORWARD = 12, STOP = 13, SKIP = 14,
            FIT = 15, FILL = 16, STRETCH = 17, SCREEN = 18, VOLUME_DOWN = 19, VOLUME_UP = 20,
            TUNE = 21, AUDIO = 22, SUBTITLES = 23, CONTINUE = 24, CLEAR_QUEUE = 25;
    private static final int LIBRARY_BASE = 100, OPEN_BASE = 200, PLAY_BASE = 300, QUEUE_BASE = 400, REMOVE_BASE = 500;
    private final long controllerPos;
    private final LegacyVideoClientState state;
    private final Deque<String> parents = new ArrayDeque<String>();
    private final List<VideoMediaItem> displayed = new ArrayList<VideoMediaItem>();
    private String libraryId = "", parentKey = "", query = "", notice = "";
    private int page, rowOffset;
    private boolean queueView;
    private GuiTextField search;
    private GuiTextField sessionName;
    private boolean acceptanceScreenshotPending;

    LegacyVideoScreen(long controllerPos, LegacyVideoClientState state) { this.controllerPos = controllerPos; this.state = state; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true); buttonList.clear(); displayed.clear();
        int panel = Math.min(760, width - 16), left = (width - panel) / 2, top = 36;
        List<VideoPackets.LibrarySummary> libraries = state.libraries().libraries(); int libraryWidth = Math.max(80, panel / Math.max(1, libraries.size()));
        int x = left;
        for (int index = 0; index < libraries.size(); index++) {
            VideoPackets.LibrarySummary library = libraries.get(index); int actual = Math.min(libraryWidth, left + panel - x);
            GuiButton button = add(LIBRARY_BASE + index, x, top, Math.max(20, actual - 2), 20, library.displayName());
            button.enabled = !library.id().equals(libraryId); x += actual;
        }
        top += 26; search = new GuiTextField(fontRendererObj, left, top, panel - 266, 20); search.setMaxStringLength(128); search.setText(query);
        add(SEARCH, left + panel - 262, top, 54, 20, "Search"); GuiButton back = add(BACK, left + panel - 204, top, 60, 20, "Back");
        back.enabled = !parents.isEmpty() && !queueView;
        add(queueView ? CLEAR_QUEUE : REFRESH, left + panel - 140, top, 64, 20, queueView ? "Clear" : "Refresh");
        add(TOGGLE_QUEUE, left + panel - 72, top, 72, 20, queueView ? "Browse" : "Queue");
        top += 28; if (queueView) addQueueRows(left, top, panel); else addBrowseRows(left, top, panel);
        addControls(left, panel);
        inspectAcceptanceLayout();
    }

    private void addBrowseRows(int left, int top, int panel) {
        VideoPackets.BrowseResults results = state.browse();
        if (!results.libraryId().equals(libraryId) || !results.parentKey().equals(parentKey)) return;
        int rows = Math.max(1, (height - top - 86) / 22); rowOffset = Math.max(0, Math.min(rowOffset, Math.max(0, results.items().size() - rows)));
        for (int row = 0; row < rows && row + rowOffset < results.items().size(); row++) {
            VideoMediaItem item = results.items().get(row + rowOffset); displayed.add(item); int y = top + row * 22;
            boolean playable = item.kind() == MediaKind.MOVIE || item.kind() == MediaKind.EPISODE;
            int actions = playable ? 104 : 52; String label = item.kind() == MediaKind.EPISODE ? "E" + item.index() + " " + item.title() : item.title();
            if (!item.parentTitle().isEmpty()) label += " - " + item.parentTitle();
            add(OPEN_BASE + row, left, y, panel - actions - 4, 20, trim(label, panel - actions - 12));
            if (playable) { add(PLAY_BASE + row, left + panel - actions, y, 50, 20, "Play"); add(QUEUE_BASE + row, left + panel - 50, y, 50, 20, "+ Queue"); }
            else add(OPEN_BASE + row, left + panel - actions, y, actions, 20, "Browse");
        }
    }

    private void addQueueRows(int left, int top, int panel) {
        List<QueuedVideo> queue = state.queue(controllerPos); int rows = Math.max(1, (height - top - 86) / 22);
        rowOffset = Math.max(0, Math.min(rowOffset, Math.max(0, queue.size() - rows)));
        for (int row = 0; row < rows && row + rowOffset < queue.size(); row++) {
            int index = row + rowOffset, y = top + row * 22; QueuedVideo entry = queue.get(index);
            add(OPEN_BASE + row, left, y, panel - 64, 20, trim((index + 1) + ". " + entry.item().title(), panel - 72)).enabled = false;
            add(REMOVE_BASE + row, left + panel - 60, y, 60, 20, "Remove");
        }
    }

    private void addControls(int left, int panel) {
        VideoPackets.SessionState playback = state.session(controllerPos); long generation = playback == null ? 0 : playback.generation();
        int y = height - 50; boolean paused = playback != null && playback.paused();
        add(PAUSE, left, y, 66, 20, paused ? "Resume" : "Pause"); add(SEEK_BACK, left + 70, y, 48, 20, "-30s");
        add(SEEK_FORWARD, left + 122, y, 48, 20, "+30s"); add(STOP, left + 174, y, 48, 20, "Stop"); add(SKIP, left + 226, y, 44, 20, "Skip");
        PresentationMode mode = mode(); add(FIT, left + 274, y, 44, 20, "Fit").enabled = mode != PresentationMode.FIT;
        add(FILL, left + 322, y, 44, 20, "Fill").enabled = mode != PresentationMode.FILL;
        add(STRETCH, left + 370, y, 58, 20, "Stretch").enabled = mode != PresentationMode.STRETCH;
        add(SCREEN, left + panel - 84, y, 84, 20, CinemarrSettings.enabled() ? "Screen on" : "Screen off");
        if (playback != null && playback.item() != null) {
            int streamY = y - 24; add(AUDIO, left, streamY, 170, 20, "Audio: " + streamLabel(playback, VideoStreamOption.Kind.AUDIO, playback.selectedAudioStreamId(), "default"));
            add(SUBTITLES, left + 174, streamY, 170, 20, "Subs: " + streamLabel(playback, VideoStreamOption.Kind.SUBTITLE, playback.selectedSubtitleStreamId(), "off"));
            if (playback.item().kind() == MediaKind.EPISODE) add(CONTINUE, left + 348, streamY, 70, 20, "Next Ep");
        }
        int bottom = height - 26; sessionName = new GuiTextField(fontRendererObj, left, bottom, 180, 20); sessionName.setMaxStringLength(64);
        add(TUNE, left + 184, bottom, 48, 20, "Tune"); add(VOLUME_DOWN, left + 240, bottom, 48, 20, "Vol -");
        add(VOLUME_UP, left + 292, bottom, 70, 20, "Vol + " + (int) Math.round(CinemarrSettings.volume() * 100) + "%");
    }

    private GuiButton add(int id, int x, int y, int width, int height, String label) { GuiButton button = new GuiButton(id, x, y, width, height, label); buttonList.add(button); return button; }

    @Override protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id >= LIBRARY_BASE && button.id < OPEN_BASE) { int index = button.id - LIBRARY_BASE; if (index < state.libraries().libraries().size()) selectLibrary(state.libraries().libraries().get(index).id()); return; }
        if (button.id >= OPEN_BASE && button.id < PLAY_BASE) { int row = button.id - OPEN_BASE; if (row < displayed.size()) activate(displayed.get(row)); return; }
        if (button.id >= PLAY_BASE && button.id < QUEUE_BASE) { int row = button.id - PLAY_BASE; if (row < displayed.size()) play(displayed.get(row)); return; }
        if (button.id >= QUEUE_BASE && button.id < REMOVE_BASE) { int row = button.id - QUEUE_BASE; if (row < displayed.size()) queue(displayed.get(row)); return; }
        if (button.id >= REMOVE_BASE) { removeQueue(rowOffset + button.id - REMOVE_BASE); return; }
        if (button.id == SEARCH) { query = search.getText().trim(); page = rowOffset = 0; request(); }
        else if (button.id == BACK) { if (!parents.isEmpty()) { parentKey = parents.pop(); query = ""; page = rowOffset = 0; request(); } }
        else if (button.id == REFRESH) request();
        else if (button.id == TOGGLE_QUEUE) { queueView = !queueView; rowOffset = 0; initGui(); }
        else if (button.id == CLEAR_QUEUE) command(VideoPackets.SessionAction.CLEAR_QUEUE, "", 0, mode(), generation(), "", -1, -1);
        else if (button.id == PAUSE) command(paused() ? VideoPackets.SessionAction.RESUME : VideoPackets.SessionAction.PAUSE, "", position(), mode(), generation(), "", -1, -1);
        else if (button.id == SEEK_BACK) seek(-30_000); else if (button.id == SEEK_FORWARD) seek(30_000);
        else if (button.id == STOP) command(VideoPackets.SessionAction.STOP, "", 0, mode(), generation(), "", -1, -1);
        else if (button.id == SKIP) command(VideoPackets.SessionAction.SKIP, "", 0, mode(), generation(), "", -1, -1);
        else if (button.id == FIT || button.id == FILL || button.id == STRETCH) command(VideoPackets.SessionAction.SET_PRESENTATION, "", 0,
                button.id == FIT ? PresentationMode.FIT : button.id == FILL ? PresentationMode.FILL : PresentationMode.STRETCH, generation(), "", -1, -1);
        else if (button.id == SCREEN) { CinemarrSettings.enabled(!CinemarrSettings.enabled()); CinemarrSettings.saveEnabled(); initGui(); }
        else if (button.id == VOLUME_DOWN || button.id == VOLUME_UP) { CinemarrSettings.volume(CinemarrSettings.volume() + (button.id == VOLUME_UP ? 0.1 : -0.1)); CinemarrSettings.saveVolume(); initGui(); }
        else if (button.id == TUNE) command(VideoPackets.SessionAction.TUNE, "", 0, mode(), generation(), sessionName.getText().trim(), -1, -1);
        else if (button.id == AUDIO) cycleStream(VideoStreamOption.Kind.AUDIO); else if (button.id == SUBTITLES) cycleStream(VideoStreamOption.Kind.SUBTITLE);
        else if (button.id == CONTINUE) command(VideoPackets.SessionAction.CONTINUE_EPISODE, "", 0, mode(), generation(), "", -1, -1);
    }

    private void activate(VideoMediaItem item) { if (item.kind() == MediaKind.SHOW || item.kind() == MediaKind.SEASON) { parents.push(parentKey); parentKey = item.key(); query = ""; page = rowOffset = 0; request(); } else play(item); }
    private void play(VideoMediaItem item) { command(VideoPackets.SessionAction.PLAY, item.key(), 0, mode(), generation(), "", -1, -1); notice = "Starting " + item.title(); }
    private void queue(VideoMediaItem item) { command(VideoPackets.SessionAction.QUEUE, item.key(), 0, mode(), generation(), "", -1, -1); notice = "Queued " + item.title(); }
    private void removeQueue(int index) { command(VideoPackets.SessionAction.REMOVE_QUEUE, "", index, mode(), generation(), "", -1, -1); }
    private void seek(long delta) { command(VideoPackets.SessionAction.SEEK, "", Math.max(0, position() + delta), mode(), generation(), "", -1, -1); }
    private void cycleStream(VideoStreamOption.Kind kind) {
        VideoPackets.SessionState playback = state.session(controllerPos); if (playback == null || playback.item() == null) return;
        List<VideoStreamOption> options = new ArrayList<VideoStreamOption>(); for (VideoStreamOption option : playback.streams()) if (option.kind() == kind) options.add(option);
        if (options.isEmpty()) return; int current = kind == VideoStreamOption.Kind.AUDIO ? playback.selectedAudioStreamId() : playback.selectedSubtitleStreamId(); int next;
        if (kind == VideoStreamOption.Kind.SUBTITLE && current < 0) next = options.get(0).id();
        else { int index = -1; for (int value = 0; value < options.size(); value++) if (options.get(value).id() == current) { index = value; break; }
            next = index + 1 < options.size() ? options.get(index + 1).id() : kind == VideoStreamOption.Kind.SUBTITLE ? -1 : options.get(0).id(); }
        command(VideoPackets.SessionAction.SET_STREAMS, playback.item().key(), playback.positionMs(), playback.presentationMode(), playback.generation(), "",
                kind == VideoStreamOption.Kind.AUDIO ? next : playback.selectedAudioStreamId(), kind == VideoStreamOption.Kind.SUBTITLE ? next : playback.selectedSubtitleStreamId());
    }
    private void command(VideoPackets.SessionAction action, String item, long seek, PresentationMode mode, long generation, String session, int audio, int subtitle) {
        state.command(new VideoPackets.SessionCommand(action, controllerPos, libraryId, item, session, mode, generation, seek, audio, subtitle));
    }
    private void selectLibrary(String id) { libraryId = id; parents.clear(); parentKey = query = ""; page = rowOffset = 0; request(); }
    private void request() { if (!libraryId.isEmpty()) state.browse(libraryId, parentKey, query, page); }
    private boolean paused() { VideoPackets.SessionState value = state.session(controllerPos); return value != null && value.paused(); }
    private long position() { VideoPackets.SessionState value = state.session(controllerPos); return value == null ? 0 : value.positionMs(); }
    private long generation() { VideoPackets.SessionState value = state.session(controllerPos); return value == null ? 0 : value.generation(); }
    private PresentationMode mode() { VideoPackets.SessionState value = state.session(controllerPos); return value == null ? PresentationMode.FIT : value.presentationMode(); }
    private static String streamLabel(VideoPackets.SessionState playback, VideoStreamOption.Kind kind, int id, String fallback) { for (VideoStreamOption option : playback.streams()) if (option.kind() == kind && option.id() == id) return option.label(); return fallback; }
    void stateChanged() { if (libraryId.isEmpty() && !state.libraries().libraries().isEmpty()) { libraryId = state.libraries().libraries().get(0).id(); request(); } initGui(); }

    @Override protected void keyTyped(char character, int keyCode) {
        if (search != null && search.textboxKeyTyped(character, keyCode)) return;
        if (sessionName != null && sessionName.textboxKeyTyped(character, keyCode)) return;
        if (keyCode == Keyboard.KEY_RETURN && search != null) { query = search.getText().trim(); page = rowOffset = 0; request(); return; }
        super.keyTyped(character, keyCode);
    }
    @Override protected void mouseClicked(int mouseX, int mouseY, int button) { super.mouseClicked(mouseX, mouseY, button); if (search != null) search.mouseClicked(mouseX, mouseY, button); if (sessionName != null) sessionName.mouseClicked(mouseX, mouseY, button); }
    @Override public void handleMouseInput() { super.handleMouseInput(); int wheel = Mouse.getEventDWheel(); if (wheel != 0) { rowOffset = Math.max(0, rowOffset + (wheel < 0 ? 1 : -1)); initGui(); } }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); drawCenteredString(fontRendererObj, "Cinemarr - Plex Video", width / 2, 10, 0xffffff);
        VideoPackets.SessionState playback = state.session(controllerPos); String now = playback == null ? "No TV state" : playback.status().name().toLowerCase(java.util.Locale.ROOT)
                + (playback.item() == null ? "" : ": " + playback.item().title() + " " + time(playback.positionMs()) + "/" + time(playback.durationMs()));
        drawCenteredString(fontRendererObj, trim(now, width - 20), width / 2, 23, 0xa0d8ff);
        super.drawScreen(mouseX, mouseY, partialTicks); if (search != null) search.drawTextBox(); if (sessionName != null) sessionName.drawTextBox();
        if (!notice.isEmpty()) drawCenteredString(fontRendererObj, trim(notice, width - 20), width / 2, height - 64, 0xffb36b);
        saveAcceptanceScreenshot();
    }
    private void inspectAcceptanceLayout() {
        if (!ProtocolLimits.videoProbeEnabled()) return;
        int widgets = 0, clipped = 0;
        for (Object value : buttonList) if (value instanceof GuiButton) {
            GuiButton button = (GuiButton) value; widgets++;
            if (button.xPosition < 0 || button.yPosition < 0 || button.xPosition + button.width > width
                    || button.yPosition + button.height > height) clipped++;
        }
        if (search != null) widgets++;
        if (sessionName != null) widgets++;
        VideoPackets.SessionState playback = state.session(controllerPos);
        boolean control = playback != null && playback.canControl();
        Cinemarr.LOGGER.info("Acceptance video UI: width={} height={} widgets={} clipped={} canControl={}",
                width, height, widgets, clipped, control);
        acceptanceScreenshotPending = true;
    }
    private void saveAcceptanceScreenshot() {
        if (!acceptanceScreenshotPending || mc == null) return;
        acceptanceScreenshotPending = false;
        IChatComponent result = ScreenShotHelper.saveScreenshot(mc.mcDataDir, "cinemarr-video-ui-acceptance.png",
                mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        Cinemarr.LOGGER.info("Acceptance video UI screenshot: {}", result.getUnformattedText());
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    private String trim(String value, int maximum) { if (fontRendererObj.getStringWidth(value) <= maximum) return value; while (value.length() > 1 && fontRendererObj.getStringWidth(value + "...") > maximum) value = value.substring(0, value.length() - 1); return value + "..."; }
    private static String time(long milliseconds) { long total = Math.max(0, milliseconds / 1000); return String.format("%d:%02d", total / 60, total % 60); }
}
