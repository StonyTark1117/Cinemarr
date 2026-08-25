package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.CinemarrNetwork;
import java.util.ArrayList;
import java.util.List;

public final class CinemarrScreen extends Screen {
    private enum View {
        NOW("cinemarr.screen.now_playing", null), SEARCH("cinemarr.screen.search_tab", CinemarrPayloads.BrowseKind.SEARCH),
        ARTISTS("cinemarr.screen.artists", CinemarrPayloads.BrowseKind.ARTISTS), ALBUMS("cinemarr.screen.albums", CinemarrPayloads.BrowseKind.ALBUMS),
        PLAYLISTS("cinemarr.screen.playlists", CinemarrPayloads.BrowseKind.PLAYLISTS), STATIONS("cinemarr.screen.stations", null),
        ADVENTURE("cinemarr.screen.adventure", CinemarrPayloads.BrowseKind.SEARCH), QUEUE("cinemarr.screen.queue", CinemarrPayloads.BrowseKind.QUEUE);
        final String label; final CinemarrPayloads.BrowseKind browseKind;
        View(String label, CinemarrPayloads.BrowseKind browseKind) { this.label = label; this.browseKind = browseKind; }
    }

    private static final int PAGE_SIZE = 20;
    private final CinemarrClientState state;
    private final List<CinemarrPayloads.StationSeed> mixSeeds = new ArrayList<>();
    private final List<CinemarrPayloads.StationSeed> adventureWaypoints = new ArrayList<>();
    private View view = View.NOW;
    private Button searchTab;
    private EditBox search;
    private boolean requestPending, queuePending;
    private String pendingQueueKey = "", pendingQuery = "", screenNotice = "", searchQuery = "";
    private CinemarrPayloads.BrowseKind pendingKind;
    private int pendingPage, rowOffset;
    private long clearArmedUntil, startNowArmedUntil;

    public CinemarrScreen(CinemarrClientState state) {
        super(Component.translatable("cinemarr.screen.title")); this.state = state;
        if (state.browse().kind() == CinemarrPayloads.BrowseKind.SEARCH) searchQuery = state.browse().query();
    }

    @Override protected void init() {
        clearWidgets(); int panelWidth = Math.min(760, width - 16), left = (width - panelWidth) / 2;
        int tabWidth = Math.max(1, panelWidth / View.values().length), tabX = left;
        for (int i = 0; i < View.values().length; i++) {
            View candidate = View.values()[i]; int actualWidth = i == View.values().length - 1 ? left + panelWidth - tabX : tabWidth;
            addTab(tabX, 38, actualWidth, candidate); tabX += actualWidth;
        }
        int contentTop = 66;
        if (view == View.SEARCH || view == View.ADVENTURE) { addSearch(left, contentTop, panelWidth); contentTop += 27; }
        switch (view) {
            case NOW -> addNowPlaying(left, contentTop, panelWidth);
            case STATIONS -> addStations(left, contentTop, panelWidth);
            case ADVENTURE -> addAdventure(left, contentTop, panelWidth);
            default -> addResults(left, contentTop, panelWidth);
        }
        addBottomControls(left, panelWidth);
        if (view.browseKind != null && view != View.ADVENTURE) addPaging(left, panelWidth);
        if (search != null) setInitialFocus(search); else if (searchTab != null) setInitialFocus(searchTab);
    }

    private void addSearch(int left, int top, int panelWidth) {
        search = addRenderableWidget(new EditBox(font, left, top, panelWidth - 126, 20, Component.translatable("cinemarr.screen.search")));
        search.setMaxLength(128); search.setHint(Component.translatable("cinemarr.screen.search")); search.setValue(searchQuery); search.setResponder(value -> searchQuery = value);
        Button go = Button.builder(Component.translatable("cinemarr.screen.go"), b -> request(0)).bounds(left + panelWidth - 122, top, 58, 20).build(); go.active = !requestPending; addRenderableWidget(go);
        Button clear = Button.builder(Component.translatable("cinemarr.screen.clear"), b -> { searchQuery = ""; search.setValue(""); request(0); })
                .bounds(left + panelWidth - 60, top, 60, 20).build(); clear.active = !requestPending; addRenderableWidget(clear);
    }

    private void addNowPlaying(int left, int top, int panelWidth) {
        CinemarrPayloads.PlaybackState playing = state.playback();
        String title = playing.title().isBlank() ? Component.translatable("cinemarr.screen.nothing_playing").getString() : playing.title();
        Button titleButton = disabled(trim(title, panelWidth - 20), left, top + 18, panelWidth); addRenderableWidget(titleButton);
        if (font.width(title) > panelWidth - 20) titleButton.setTooltip(Tooltip.create(Component.literal(title)));
        if (!playing.artist().isBlank()) addRenderableWidget(disabled(trim(playing.artist(), panelWidth - 20), left, top + 42, panelWidth));
        String source = playing.sourceName().isBlank() ? switch (playing.origin()) {
            case MANUAL -> "Manual request"; case STATION -> "Station"; case ADVENTURE -> "Sonic Adventure"; case NONE -> "";
        } : playing.sourceName();
        if (!source.isBlank()) addRenderableWidget(disabled("Source: " + source, left, top + 66, panelWidth));
        if (state.audioState() == AudioPlaybackState.ERROR)
            addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.retry_audio"), b -> state.retryAudio()).bounds(left + panelWidth / 2 - 48, top + 92, 96, 20).build());
    }

    private void addStations(int left, int top, int panelWidth) {
        CinemarrPayloads.StationState station = state.station();
        addRenderableWidget(disabled(capabilityLabel(station), left, top, panelWidth));
        addRenderableWidget(disabled(station.active() && station.stationType() != CinemarrPayloads.StationType.SONIC_ADVENTURE
                ? "Active: " + station.name() : "No general station active", left, top + 23, panelWidth));
        if (!state.playback().operator()) return;
        Button autoplay = Button.builder(Component.literal("Autoplay: " + (station.autoplayEnabled() ? "On" : "Off")), b ->
                stationRequest(CinemarrPayloads.StationAction.SET_AUTOPLAY, CinemarrPayloads.StationType.AUTOPLAY, !station.autoplayEnabled(), List.of()))
                .bounds(left, top + 48, 110, 20).build(); addRenderableWidget(autoplay);
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.library_shuffle"), b ->
                stationRequest(CinemarrPayloads.StationAction.START, CinemarrPayloads.StationType.LIBRARY_SHUFFLE, false, List.of()))
                .bounds(left + 116, top + 48, 126, 20).build());
        Button stop = Button.builder(Component.translatable("cinemarr.screen.stop_station"), b ->
                stationRequest(CinemarrPayloads.StationAction.STOP, CinemarrPayloads.StationType.NONE, false, List.of()))
                .bounds(left + 248, top + 48, 100, 20).build(); stop.active = station.active(); addRenderableWidget(stop);

        addRenderableWidget(disabled("Sonic Mix seeds (2-5, one type)", left, top + 76, panelWidth));
        for (int i = 0; i < Math.min(5, mixSeeds.size()); i++) {
            int index = i, y = top + 99 + i * 22; CinemarrPayloads.StationSeed seed = mixSeeds.get(i);
            addRenderableWidget(disabled((i + 1) + ". " + seed.title() + (seed.subtitle().isBlank() ? "" : " — " + seed.subtitle()), left, y, panelWidth - 34));
            addRenderableWidget(Button.builder(Component.literal("×"), b -> { mixSeeds.remove(index); rebuildWidgets(); }).bounds(left + panelWidth - 30, y, 30, 20).build());
        }
        int actionY = top + 99 + Math.min(5, mixSeeds.size()) * 22;
        Button start = Button.builder(Component.translatable("cinemarr.screen.start"), b ->
                stationRequest(CinemarrPayloads.StationAction.START, CinemarrPayloads.StationType.SONIC_MIX, false, mixSeeds)).bounds(left, actionY, 78, 20).build();
        start.active = mixSeeds.size() >= 2; addRenderableWidget(start);
        Button startNow = Button.builder(Component.translatable("cinemarr.screen.start_now"), b -> confirmStartNow(CinemarrPayloads.StationType.SONIC_MIX, mixSeeds))
                .bounds(left + 84, actionY, 92, 20).build(); startNow.active = mixSeeds.size() >= 2; addRenderableWidget(startNow);
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.clear_builder"), b -> { mixSeeds.clear(); rebuildWidgets(); })
                .bounds(left + 182, actionY, 102, 20).build());
        int previewY = actionY + 26;
        if (!station.preview().isEmpty()) {
            addRenderableWidget(disabled("Generated next:", left, previewY, panelWidth));
            for (int i = 0; i < station.preview().size(); i++) addRenderableWidget(disabled("  " + station.preview().get(i).title() + " — " + station.preview().get(i).artist(), left, previewY + 22 + i * 22, panelWidth));
        }
    }

    private void addAdventure(int left, int top, int panelWidth) {
        CinemarrPayloads.StationState station = state.station();
        addRenderableWidget(disabled(capabilityLabel(station), left, top, panelWidth));
        addRenderableWidget(disabled(station.stationType() == CinemarrPayloads.StationType.SONIC_ADVENTURE ? "Active: " + station.name() : "Adventure waypoints (2-5 tracks)", left, top + 23, panelWidth));
        if (!state.playback().operator()) return;
        int listTop = top + 46;
        for (int i = 0; i < adventureWaypoints.size(); i++) {
            int index = i, y = listTop + i * 22; CinemarrPayloads.StationSeed seed = adventureWaypoints.get(i);
            addRenderableWidget(disabled((i + 1) + ". " + seed.title() + (seed.subtitle().isBlank() ? "" : " — " + seed.subtitle()), left, y, panelWidth - 94));
            Button up = Button.builder(Component.literal("↑"), b -> moveWaypoint(index, -1)).bounds(left + panelWidth - 90, y, 28, 20).build(); up.active = i > 0; addRenderableWidget(up);
            Button down = Button.builder(Component.literal("↓"), b -> moveWaypoint(index, 1)).bounds(left + panelWidth - 60, y, 28, 20).build(); down.active = i + 1 < adventureWaypoints.size(); addRenderableWidget(down);
            addRenderableWidget(Button.builder(Component.literal("×"), b -> { adventureWaypoints.remove(index); rebuildWidgets(); }).bounds(left + panelWidth - 30, y, 30, 20).build());
        }
        int actionsY = listTop + adventureWaypoints.size() * 22;
        Button preview = Button.builder(Component.translatable("cinemarr.screen.preview"), b -> stationRequest(CinemarrPayloads.StationAction.PREVIEW_ADVENTURE,
                CinemarrPayloads.StationType.SONIC_ADVENTURE, false, adventureWaypoints)).bounds(left, actionsY, 76, 20).build(); preview.active = adventureWaypoints.size() >= 2; addRenderableWidget(preview);
        Button start = Button.builder(Component.translatable("cinemarr.screen.start"), b -> stationRequest(CinemarrPayloads.StationAction.START,
                CinemarrPayloads.StationType.SONIC_ADVENTURE, false, adventureWaypoints)).bounds(left + 82, actionsY, 76, 20).build(); start.active = adventureWaypoints.size() >= 2; addRenderableWidget(start);
        Button startNow = Button.builder(Component.translatable("cinemarr.screen.start_now"), b -> confirmStartNow(CinemarrPayloads.StationType.SONIC_ADVENTURE, adventureWaypoints))
                .bounds(left + 164, actionsY, 92, 20).build(); startNow.active = adventureWaypoints.size() >= 2; addRenderableWidget(startNow);
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.clear_builder"), b -> { adventureWaypoints.clear(); rebuildWidgets(); })
                .bounds(left + 262, actionsY, 104, 20).build());
        int resultsTop = actionsY + 26;
        CinemarrPayloads.AdventurePreview path = state.adventurePreview();
        if (!path.message().isBlank()) {
            addRenderableWidget(disabled(path.message(), left, resultsTop, panelWidth));
            for (int i = 0; i < Math.min(3, path.path().size()); i++) addRenderableWidget(disabled("  " + path.path().get(i).title() + " — " + path.path().get(i).artist(), left, resultsTop + 22 + i * 22, panelWidth));
            resultsTop += 92;
        }
        addAdventureSearchResults(left, resultsTop, panelWidth);
    }

    private void addAdventureSearchResults(int left, int top, int panelWidth) {
        CinemarrPayloads.BrowseResults results = state.browse();
        if (results.kind() != CinemarrPayloads.BrowseKind.SEARCH || requestPending) return;
        int rows = Math.max(0, Math.min(4, (height - top - 54) / 22));
        for (int i = 0; i < rows && i < results.items().size(); i++) {
            CinemarrPayloads.MediaItem item = results.items().get(i); if (item.kind() != CinemarrPayloads.ItemKind.TRACK) continue;
            int y = top + i * 22; addRenderableWidget(disabled(item.title() + " — " + item.subtitle(), left, y, panelWidth - 40));
            Button add = Button.builder(Component.literal("A"), b -> addAdventure(item)).bounds(left + panelWidth - 36, y, 36, 20).build();
            add.setTooltip(Tooltip.create(Component.literal("Add as Adventure waypoint"))); add.active = adventureWaypoints.size() < 5; addRenderableWidget(add);
        }
    }

    private void addResults(int left, int contentTop, int panelWidth) {
        CinemarrPayloads.BrowseResults results = state.browse(); if (view.browseKind == null || results.kind() != view.browseKind || requestPending) return;
        List<CinemarrPayloads.MediaItem> items = results.items(); int rows = Math.max(1, (height - contentTop - 54) / 22);
        rowOffset = Math.max(0, Math.min(rowOffset, Math.max(0, items.size() - rows)));
        for (int row = 0; row < rows && row + rowOffset < items.size(); row++) {
            int localIndex = row + rowOffset, queueIndex = results.page() * PAGE_SIZE + localIndex, y = contentTop + row * 22;
            CinemarrPayloads.MediaItem item = items.get(localIndex); CinemarrPayloads.QueueEntry queueEntry = queueIndex < state.playback().queue().size() ? state.playback().queue().get(queueIndex) : null;
            String prefix = view == View.QUEUE ? (queueIndex == 0 ? "▶ " : (queueIndex + 1) + ". ") : "";
            String duration = item.durationMs() > 0 ? " (" + time(item.durationMs()) + ")" : "";
            String source = view == View.QUEUE && queueEntry != null && queueEntry.source() != CinemarrPayloads.PlaybackOrigin.MANUAL ? " [" + queueEntry.source().name().toLowerCase() + "]" : "";
            String label = prefix + item.title() + (item.subtitle().isBlank() ? "" : " — " + item.subtitle()) + duration + source;
            int controlsWidth = controlsWidth(item, queueEntry); Button itemButton = disabled(trim(label, panelWidth - controlsWidth - 20), left, y, panelWidth - controlsWidth - 4);
            if (font.width(label) > panelWidth - controlsWidth - 20) itemButton.setTooltip(Tooltip.create(Component.literal(label))); addRenderableWidget(itemButton);
            if (view == View.QUEUE && queueEntry != null && queueEntry.editable() && state.playback().operator()) addQueueControls(left, panelWidth, y, queueIndex, queueEntry);
            else if (view != View.QUEUE) addBrowseControls(left, panelWidth, y, item);
        }
    }

    private int controlsWidth(CinemarrPayloads.MediaItem item, CinemarrPayloads.QueueEntry queueEntry) {
        if (view == View.QUEUE) return queueEntry != null && queueEntry.editable() && state.playback().operator() ? 90 : 4;
        if (!state.playback().operator() || item.kind() == CinemarrPayloads.ItemKind.PLAYLIST) return 40;
        return item.kind() == CinemarrPayloads.ItemKind.TRACK ? 124 : 94;
    }
    private void addQueueControls(int left, int panelWidth, int y, int index, CinemarrPayloads.QueueEntry entry) {
        int x = left + panelWidth - 88; Button up = Button.builder(Component.literal("↑"), b -> control(CinemarrPayloads.ControlAction.MOVE_UP, index, entry.key())).bounds(x, y, 28, 20).build();
        Button down = Button.builder(Component.literal("↓"), b -> control(CinemarrPayloads.ControlAction.MOVE_DOWN, index, entry.key())).bounds(x + 30, y, 28, 20).build();
        up.active = index > 0 && state.playback().queue().get(index - 1).editable(); down.active = index + 1 < state.playback().queue().size() && state.playback().queue().get(index + 1).editable();
        addRenderableWidget(up); addRenderableWidget(down); addRenderableWidget(Button.builder(Component.literal("×"), b -> control(CinemarrPayloads.ControlAction.REMOVE, index, entry.key())).bounds(x + 60, y, 28, 20).build());
    }
    private void addBrowseControls(int left, int panelWidth, int y, CinemarrPayloads.MediaItem item) {
        int button = 28, gap = 2, actions = state.playback().operator() && item.kind() != CinemarrPayloads.ItemKind.PLAYLIST ? (item.kind() == CinemarrPayloads.ItemKind.TRACK ? 4 : 3) : 1;
        int x = left + panelWidth - actions * (button + gap);
        addAction("+", "Add to manual queue", x, y, b -> activate(item)); x += button + gap;
        if (!state.playback().operator() || item.kind() == CinemarrPayloads.ItemKind.PLAYLIST) return;
        addAction("R", "Start radio after manual requests", x, y, b -> startRadio(item)); x += button + gap;
        addAction("M", "Add to Sonic Mix", x, y, b -> addMix(item)); x += button + gap;
        if (item.kind() == CinemarrPayloads.ItemKind.TRACK) addAction("A", "Add as Adventure waypoint", x, y, b -> addAdventure(item));
    }
    private void addAction(String label, String tooltip, int x, int y, Button.OnPress press) {
        Button value = Button.builder(Component.literal(label), press).bounds(x, y, 28, 20).build(); value.setTooltip(Tooltip.create(Component.literal(tooltip))); addRenderableWidget(value);
    }

    private void addBottomControls(int left, int panelWidth) {
        int bottom = height - 27;
        addRenderableWidget(Button.builder(Component.translatable(CinemarrSettings.enabled() ? "cinemarr.screen.mute" : "cinemarr.screen.unmute"), b -> {
            CinemarrSettings.enabled(!CinemarrSettings.enabled()); CinemarrSettings.saveEnabled(); state.listeningChanged(); rebuildWidgets();
        }).bounds(left, bottom, 68, 20).build()); addRenderableWidget(new VolumeSlider(left + 74, bottom, 130, 20));
        if (state.playback().operator()) {
            addRenderableWidget(Button.builder(Component.translatable(state.playback().paused() ? "cinemarr.screen.resume" : "cinemarr.screen.pause"), b ->
                    control(state.playback().paused() ? CinemarrPayloads.ControlAction.RESUME : CinemarrPayloads.ControlAction.PAUSE, -1)).bounds(left + 210, bottom, 72, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.skip"), b -> control(CinemarrPayloads.ControlAction.SKIP, -1)).bounds(left + 288, bottom, 58, 20).build());
            boolean armed = System.currentTimeMillis() < clearArmedUntil;
            addRenderableWidget(Button.builder(Component.translatable(armed ? "cinemarr.screen.confirm" : "cinemarr.screen.clear"), b -> {
                if (System.currentTimeMillis() < clearArmedUntil) { clearArmedUntil = 0; control(CinemarrPayloads.ControlAction.CLEAR, -1); }
                else { clearArmedUntil = System.currentTimeMillis() + 5_000; screenNotice = Component.translatable("cinemarr.screen.confirm_clear_notice").getString(); rebuildWidgets(); }
            }).bounds(left + 352, bottom, 72, 20).build());
        }
    }
    private void addPaging(int left, int panelWidth) {
        CinemarrPayloads.BrowseResults results = state.browse(); if (view.browseKind == null || results.kind() != view.browseKind) return; int bottom = height - 27;
        if (results.page() > 0) { Button previous = Button.builder(Component.literal("<"), b -> request(results.page() - 1)).bounds(left + panelWidth - 70, bottom, 32, 20).build(); previous.active = !requestPending; addRenderableWidget(previous); }
        if (results.hasMore()) { Button next = Button.builder(Component.literal(">"), b -> request(results.page() + 1)).bounds(left + panelWidth - 34, bottom, 32, 20).build(); next.active = !requestPending; addRenderableWidget(next); }
    }

    private void addTab(int x, int y, int width, View candidate) {
        Button button = Button.builder(Component.translatable(candidate.label), b -> {
            view = candidate; rowOffset = 0; requestPending = false; queuePending = false; pendingQueueKey = ""; screenNotice = ""; state.clearNotice();
            if (candidate.browseKind != null && candidate != View.ADVENTURE) request(0); rebuildWidgets();
        }).bounds(x, y, width, 20).build(); if (candidate == View.SEARCH) searchTab = button; button.active = view != candidate; addRenderableWidget(button);
    }
    private void activate(CinemarrPayloads.MediaItem item) {
        if (queuePending) return; queuePending = true; pendingQueueKey = item.key(); screenNotice = Component.translatable("cinemarr.screen.queuing").getString();
        CinemarrNetwork.sendToServer(new CinemarrPayloads.QueueRequest(item.kind(), item.key())); rebuildWidgets();
    }
    private void startRadio(CinemarrPayloads.MediaItem item) {
        CinemarrPayloads.StationType type = switch (item.kind()) { case TRACK -> CinemarrPayloads.StationType.TRACK_RADIO; case ARTIST -> CinemarrPayloads.StationType.ARTIST_RADIO; case ALBUM -> CinemarrPayloads.StationType.ALBUM_RADIO; case PLAYLIST -> CinemarrPayloads.StationType.NONE; };
        if (type != CinemarrPayloads.StationType.NONE) stationRequest(CinemarrPayloads.StationAction.START, type, false, List.of(seed(item)));
    }
    private void addMix(CinemarrPayloads.MediaItem item) {
        if (item.kind() == CinemarrPayloads.ItemKind.PLAYLIST) return;
        if (!mixSeeds.isEmpty() && mixSeeds.get(0).kind() != item.kind()) { screenNotice = "Sonic Mix seeds must all be the same type"; rebuildWidgets(); return; }
        if (mixSeeds.size() >= 5 || mixSeeds.stream().anyMatch(seed -> seed.key().equals(item.key()))) return;
        mixSeeds.add(seed(item)); screenNotice = "Added to Sonic Mix builder"; rebuildWidgets();
    }
    private void addAdventure(CinemarrPayloads.MediaItem item) {
        if (item.kind() != CinemarrPayloads.ItemKind.TRACK || adventureWaypoints.size() >= 5 || adventureWaypoints.stream().anyMatch(seed -> seed.key().equals(item.key()))) return;
        adventureWaypoints.add(seed(item)); screenNotice = "Added Adventure waypoint"; rebuildWidgets();
    }
    private void moveWaypoint(int index, int delta) { int target = index + delta; if (target < 0 || target >= adventureWaypoints.size()) return; CinemarrPayloads.StationSeed value = adventureWaypoints.remove(index); adventureWaypoints.add(target, value); rebuildWidgets(); }
    private static CinemarrPayloads.StationSeed seed(CinemarrPayloads.MediaItem item) { return new CinemarrPayloads.StationSeed(item.kind(), item.key(), item.title(), item.subtitle()); }
    private void stationRequest(CinemarrPayloads.StationAction action, CinemarrPayloads.StationType type, boolean enabled, List<CinemarrPayloads.StationSeed> seeds) {
        CinemarrNetwork.sendToServer(new CinemarrPayloads.StationRequest(action, type, enabled, state.station().generation(), List.copyOf(seeds))); screenNotice = "Updating shared playback source…";
    }
    private void confirmStartNow(CinemarrPayloads.StationType type, List<CinemarrPayloads.StationSeed> seeds) {
        if (System.currentTimeMillis() < startNowArmedUntil) { startNowArmedUntil = 0; stationRequest(CinemarrPayloads.StationAction.START_NOW, type, false, seeds); }
        else { startNowArmedUntil = System.currentTimeMillis() + 5_000; screenNotice = "Press Start Now again to clear manual requests and replace current playback"; rebuildWidgets(); }
    }
    private void control(CinemarrPayloads.ControlAction action, int index) { control(action, index, ""); }
    private void control(CinemarrPayloads.ControlAction action, int index, String expectedKey) { CinemarrNetwork.sendToServer(new CinemarrPayloads.ControlRequest(action, index, expectedKey)); }

    private void request(int page) {
        if (view.browseKind == null || requestPending) return; if (search != null) searchQuery = search.getValue(); searchQuery = searchQuery.trim();
        if (view.browseKind == CinemarrPayloads.BrowseKind.QUEUE) {
            requestPending = false; state.showQueuePage(page); rebuildWidgets(); return;
        }
        if ((view == View.SEARCH || view == View.ADVENTURE) && searchQuery.length() < 2) {
            requestPending = false; screenNotice = Component.translatable("cinemarr.screen.short_query").getString(); state.clearBrowse(view.browseKind, searchQuery); rebuildWidgets(); return;
        }
        String requestQuery = browseQuery(view.browseKind, searchQuery);
        requestPending = true; pendingKind = view.browseKind; pendingQuery = requestQuery; pendingPage = page;
        CinemarrNetwork.sendToServer(new CinemarrPayloads.BrowseRequest(view.browseKind, requestQuery, page)); rebuildWidgets();
    }
    static String browseQuery(CinemarrPayloads.BrowseKind kind, String searchQuery) {
        return kind == CinemarrPayloads.BrowseKind.SEARCH ? searchQuery.trim() : "";
    }
    void resultsChanged() { CinemarrPayloads.BrowseResults result = state.browse(); if (requestPending && result.kind() == pendingKind && result.page() == pendingPage && result.query().equals(pendingQuery)) requestPending = false; if (minecraft != null) rebuildWidgets(); }
    void requestFailed() { requestPending = false; queuePending = false; pendingQueueKey = ""; if (minecraft != null) rebuildWidgets(); }
    void playbackChanged() { if (!queuePending || state.playback().queue().stream().noneMatch(entry -> entry.key().equals(pendingQueueKey) && entry.source() == CinemarrPayloads.PlaybackOrigin.MANUAL)) return; queuePending = false; pendingQueueKey = ""; screenNotice = Component.translatable("cinemarr.screen.queued").getString(); rebuildWidgets(); }
    void queueChanged() { if (view == View.QUEUE && minecraft != null) rebuildWidgets(); }
    void stationChanged() { if (minecraft != null && (view == View.STATIONS || view == View.ADVENTURE || view == View.NOW || view == View.QUEUE)) rebuildWidgets(); }
    void adventurePreviewChanged() { if (minecraft != null && view == View.ADVENTURE) rebuildWidgets(); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) { if (key == 257 && search != null && search.isFocused()) { request(0); return true; } return super.keyPressed(key, scanCode, modifiers); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) { if (scrollY != 0 && view.browseKind != null && state.browse().kind() == view.browseKind) { rowOffset = Math.max(0, rowOffset + (scrollY < 0 ? 1 : -1)); rebuildWidgets(); return true; } return super.mouseScrolled(mouseX, mouseY, scrollY); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); super.render(graphics, mouseX, mouseY, partialTick); graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        CinemarrPayloads.PlaybackState playing = state.playback(); String now = statusLabel(playing) + (playing.title().isBlank() ? "" : ": " + playing.title() + (playing.artist().isBlank() ? "" : " — " + playing.artist())) + "  " + time(playing.positionMs()) + "/" + time(playing.durationMs());
        graphics.drawCenteredString(font, trim(now, width - 20), width / 2, 25, statusColor(playing.status()));
        String notice = screenNotice.isBlank() ? (state.notice().isBlank() ? playing.statusMessage() : state.notice()) : screenNotice;
        if (!notice.isBlank()) graphics.drawCenteredString(font, trim(notice, width - 24), width / 2, height - 40, 0xFFB36B);
        if (view == View.NOW) graphics.drawCenteredString(font, "Audio: " + state.audioStatus(), width / 2, 187, state.audioState() == AudioPlaybackState.ERROR ? 0xFF7777 : 0xA0D8FF);
        if (requestPending) graphics.drawCenteredString(font, Component.translatable("cinemarr.screen.searching"), width / 2, height / 2, 0xA0D8FF);
        else if (queuePending) graphics.drawCenteredString(font, Component.translatable("cinemarr.screen.queuing"), width / 2, height / 2, 0xA0D8FF);
        else if (playing.status() == CinemarrPayloads.PlaybackStatus.PLEX_OFFLINE && notice.isBlank()) graphics.drawCenteredString(font, Component.translatable("cinemarr.screen.plex_unavailable"), width / 2, height / 2, 0xFF7777);
    }

    private Button disabled(String value, int x, int y, int width) { Button button = Button.builder(Component.literal(trim(value, width - 12)), b -> {}).bounds(x, y, width, 20).build(); button.active = false; return button; }
    private static String capabilityLabel(CinemarrPayloads.StationState station) { return "Sonic: " + station.capability().name().replace('_', ' ').toLowerCase() + " — " + station.capabilityMessage(); }
    private static String statusLabel(CinemarrPayloads.PlaybackState state) { return Component.translatable(switch (state.status()) { case IDLE -> "cinemarr.status.idle"; case PREPARING -> "cinemarr.status.preparing"; case PLAYING -> "cinemarr.status.playing"; case PAUSED -> "cinemarr.status.paused"; case PLEX_OFFLINE -> "cinemarr.status.plex_offline"; }).getString(); }
    private static int statusColor(CinemarrPayloads.PlaybackStatus status) { return status == CinemarrPayloads.PlaybackStatus.PLEX_OFFLINE ? 0xFF7777 : status == CinemarrPayloads.PlaybackStatus.PREPARING ? 0xFFD37A : 0xCFCFCF; }
    private String trim(String value, int pixels) { return font.width(value) <= pixels ? value : font.plainSubstrByWidth(value, Math.max(0, pixels - font.width("…"))) + "…"; }
    private static String time(long ms) { long seconds = Math.max(0, ms / 1000); return "%d:%02d".formatted(seconds / 60, seconds % 60); }
    @Override public boolean isPauseScreen() { return false; }

    private final class VolumeSlider extends AbstractSliderButton {
        private VolumeSlider(int x, int y, int width, int height) { super(x, y, width, height, Component.empty(), CinemarrSettings.volume()); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("cinemarr.screen.volume", Math.round(value * 100))); }
        @Override protected void applyValue() { CinemarrSettings.volume(value); CinemarrSettings.saveVolume(); }
    }
}
