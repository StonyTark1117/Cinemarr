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
import stonytark.cinemarr.core.client.VideoControllerLayout;
import stonytark.cinemarr.core.client.VideoControllerLayout.Box;
import stonytark.cinemarr.core.client.VideoControllerLayout.Slot;
import stonytark.cinemarr.core.client.VideoControllerFeedback;
import stonytark.cinemarr.Cinemarr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Controller-specific movie/show browser and synchronized TV controls for Forge 1.7.10. */
final class LegacyVideoScreen extends GuiScreen {
    private static final int SEARCH = 1, BACK = 2, REFRESH = 3, TOGGLE_QUEUE = 4;
    private static final int PREVIOUS_PAGE=5, NEXT_PAGE=6, PAGE_LABEL=7, LIBRARY_PREVIOUS=8, LIBRARY_NEXT=9, VOLUME_LABEL=26;
    private static final int PAUSE = 10, SEEK_BACK = 11, SEEK_FORWARD = 12, STOP = 13, SKIP = 14,
            FIT = 15, FILL = 16, STRETCH = 17, SCREEN = 18, VOLUME_DOWN = 19, VOLUME_UP = 20,
            TUNE = 21, AUDIO = 22, SUBTITLES = 23, CONTINUE = 24, CLEAR_QUEUE = 25;
    private static final int LIBRARY_BASE = 100, OPEN_BASE = 200, PLAY_BASE = 300, QUEUE_BASE = 400, REMOVE_BASE = 500;
    private final long controllerPos;
    private final LegacyVideoClientState state;
    private final Deque<String> parents = new ArrayDeque<String>();
    private final List<VideoMediaItem> displayed = new ArrayList<VideoMediaItem>();
    private final java.util.Map<GuiButton,String> tooltips=new java.util.IdentityHashMap<GuiButton,String>();
    private String libraryId = "", parentKey = "", query = "";
    private final VideoControllerFeedback feedback = new VideoControllerFeedback();
    private int page, rowOffset, libraryPage;
    private VideoControllerLayout layout;
    private boolean queueView;
    private GuiTextField search;
    private GuiTextField sessionName;
    private boolean acceptanceScreenshotPending;

    LegacyVideoScreen(long controllerPos, LegacyVideoClientState state) { this.controllerPos = controllerPos; this.state = state; }

    @Override public void initGui() {
        VideoPackets.SessionState authoritative=state.session(controllerPos);
        feedback.updateServerMessage(authoritative==null?"":authoritative.message());
        LegacyTextFieldState previousSearch=search==null?null:LegacyTextFieldState.capture(search);
        LegacyTextFieldState previousSession=sessionName==null?null:LegacyTextFieldState.capture(sessionName);
        Keyboard.enableRepeatEvents(true); buttonList.clear(); displayed.clear();tooltips.clear();
        layout=new VideoControllerLayout(width,height);
        int panel=layout.panel(),left=layout.left(),top=layout.contentTop();
        List<VideoPackets.LibrarySummary> libraries = state.libraries().libraries();
        libraryPage=Math.max(0,Math.min(libraryPage,layout.libraryPages(libraries.size())-1));
        control(LIBRARY_PREVIOUS,Slot.LIBRARY_PREVIOUS,"<").enabled=libraryPage>0;
        control(LIBRARY_NEXT,Slot.LIBRARY_NEXT,">").enabled=libraryPage+1<layout.libraryPages(libraries.size());
        for (int slot=0;slot<layout.libraryCapacity();slot++) {
            int index=libraryPage*layout.libraryCapacity()+slot;if(index>=libraries.size())break;
            VideoPackets.LibrarySummary library=libraries.get(index);Box box=layout.library(slot);
            GuiButton button=add(LIBRARY_BASE+index,box.x,box.y,box.width,box.height,library.displayName());
            button.enabled = !library.id().equals(libraryId);
        }
        Box searchBox=layout.slot(Slot.SEARCH);search=new GuiTextField(fontRendererObj,searchBox.x,searchBox.y,searchBox.width,searchBox.height);search.setMaxStringLength(128);if(previousSearch==null)search.setText(query);else previousSearch.restore(search);
        control(SEARCH,Slot.GO,"Go");GuiButton back=control(BACK,Slot.BACK,"Back");
        back.enabled = !parents.isEmpty() && !queueView;
        control(queueView?CLEAR_QUEUE:REFRESH,Slot.REFRESH,queueView?"Clear":"Refresh");
        control(TOGGLE_QUEUE,Slot.QUEUE,queueView?"Browse":"Queue");
        if (queueView) addQueueRows(left, top, panel); else addBrowseRows(left, top, panel);
        addControls();
        if(previousSession!=null)previousSession.restore(sessionName);
        inspectAcceptanceLayout();
    }

    private void addBrowseRows(int left, int top, int panel) {
        VideoPackets.BrowseResults results = state.browse();
        if (!results.libraryId().equals(libraryId) || !results.parentKey().equals(parentKey)
                || !results.query().equals(query) || results.page()!=page) return;
        int rows = layout.rows(false); rowOffset = Math.max(0, Math.min(rowOffset, Math.max(0, results.items().size() - rows)));
        for (int row = 0; row < rows && row + rowOffset < results.items().size(); row++) {
            VideoMediaItem item = results.items().get(row + rowOffset); displayed.add(item); int y = top + row * 22;
            boolean playable = item.kind() == MediaKind.MOVIE || item.kind() == MediaKind.EPISODE;
            int actions = playable ? 104 : 52; String label = item.kind() == MediaKind.EPISODE ? "E" + item.index() + " " + item.title() : item.title();
            if (!item.parentTitle().isEmpty()) label += " - " + item.parentTitle();
            add(OPEN_BASE + row, left, y, panel - actions - 4, 20, trim(label, panel - actions - 12));
            if (playable) { add(PLAY_BASE + row, left + panel - actions, y, 50, 20, "Play"); add(QUEUE_BASE + row, left + panel - 50, y, 50, 20, "+ Queue"); }
            else add(OPEN_BASE + row, left + panel - actions, y, actions, 20, "Browse");
        }
        int pagerY=layout.pagerY();
        add(PREVIOUS_PAGE,left,pagerY,60,20,"< Prev").enabled=page>0;
        add(PAGE_LABEL,left+64,pagerY,72,20,"Page "+(page+1)).enabled=false;
        add(NEXT_PAGE,left+140,pagerY,60,20,"Next >").enabled=results.hasMore();
    }

    private void addQueueRows(int left, int top, int panel) {
        List<QueuedVideo> queue = state.queue(controllerPos); int rows = layout.rows(true);
        rowOffset = Math.max(0, Math.min(rowOffset, Math.max(0, queue.size() - rows)));
        for (int row = 0; row < rows && row + rowOffset < queue.size(); row++) {
            int index = row + rowOffset, y = top + row * 22; QueuedVideo entry = queue.get(index);
            add(OPEN_BASE + row, left, y, panel - 64, 20, trim((index + 1) + ". " + entry.item().title(), panel - 72)).enabled = false;
            add(REMOVE_BASE + row, left + panel - 60, y, 60, 20, "Remove");
        }
    }

    private void addControls() {
        VideoPackets.SessionState playback=state.session(controllerPos);
        boolean paused=playback!=null&&playback.paused();
        control(PAUSE,Slot.PAUSE,paused?"Resume":"Pause");control(SEEK_BACK,Slot.SEEK_BACK,"-30s");
        control(SEEK_FORWARD,Slot.SEEK_FORWARD,"+30s");control(STOP,Slot.STOP,"Stop");control(SKIP,Slot.SKIP,"Skip");
        PresentationMode mode=mode();control(FIT,Slot.FIT,"Fit").enabled=mode!=PresentationMode.FIT;
        control(FILL,Slot.FILL,"Fill").enabled=mode!=PresentationMode.FILL;
        control(STRETCH,Slot.STRETCH,"Stretch").enabled=mode!=PresentationMode.STRETCH;
        control(SCREEN,Slot.SCREEN,CinemarrSettings.enabled()?"Screen on":"Screen off");
        if(playback!=null&&playback.item()!=null){
            control(AUDIO,Slot.AUDIO,"Audio: "+streamLabel(playback,VideoStreamOption.Kind.AUDIO,playback.selectedAudioStreamId(),"default"));
            control(SUBTITLES,Slot.SUBTITLES,"Subs: "+streamLabel(playback,VideoStreamOption.Kind.SUBTITLE,playback.selectedSubtitleStreamId(),"off"));
            if(playback.item().kind()==MediaKind.EPISODE)control(CONTINUE,Slot.CONTINUE,"Next Ep");
        }
        Box box=layout.slot(Slot.SESSION);sessionName=new GuiTextField(fontRendererObj,box.x,box.y,box.width,box.height);sessionName.setMaxStringLength(64);
        control(TUNE,Slot.TUNE,"Tune");control(VOLUME_DOWN,Slot.VOLUME_DOWN,"Vol -");
        control(VOLUME_LABEL,Slot.VOLUME,(int)Math.round(CinemarrSettings.volume()*100)+"%").enabled=false;
        control(VOLUME_UP,Slot.VOLUME_UP,"Vol +");
    }

    private GuiButton control(int id,Slot slot,String label){Box box=layout.slot(slot);return add(id,box.x,box.y,box.width,box.height,label);}

    private GuiButton add(int id, int x, int y, int width, int height, String label) { String display=trim(label,width-8);GuiButton button = new LegacyVideoButton(id, x, y, width, height, display); buttonList.add(button);if(!display.equals(label))tooltips.put(button,label);return button; }

    @Override protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id >= LIBRARY_BASE && button.id < OPEN_BASE) { int index = button.id - LIBRARY_BASE; if (index < state.libraries().libraries().size()) selectLibrary(state.libraries().libraries().get(index).id()); return; }
        if (button.id >= OPEN_BASE && button.id < PLAY_BASE) { int row = button.id - OPEN_BASE; if (row < displayed.size()) activate(displayed.get(row)); return; }
        if (button.id >= PLAY_BASE && button.id < QUEUE_BASE) { int row = button.id - PLAY_BASE; if (row < displayed.size()) play(displayed.get(row)); return; }
        if (button.id >= QUEUE_BASE && button.id < REMOVE_BASE) { int row = button.id - QUEUE_BASE; if (row < displayed.size()) queue(displayed.get(row)); return; }
        if (button.id >= REMOVE_BASE) { removeQueue(rowOffset + button.id - REMOVE_BASE); return; }
        if(button.id==LIBRARY_PREVIOUS){libraryPage--;initGui();}
        else if(button.id==LIBRARY_NEXT){libraryPage++;initGui();}
        else if(button.id==PREVIOUS_PAGE){page=Math.max(0,page-1);rowOffset=0;request();}
        else if(button.id==NEXT_PAGE){if(state.browse().page()==page&&state.browse().hasMore()){page++;rowOffset=0;request();}}
        else if (button.id == SEARCH) { queueView=false;query = search.getText().trim(); page = rowOffset = 0; request(); }
        else if (button.id == BACK) { if (!parents.isEmpty()) { parentKey = parents.pop(); query = "";if(search!=null)search.setText(query); page = rowOffset = 0; request(); } }
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

    private void activate(VideoMediaItem item) { if (item.kind() == MediaKind.SHOW || item.kind() == MediaKind.SEASON) { parents.push(parentKey); parentKey = item.key(); query = "";if(search!=null)search.setText(query); page = rowOffset = 0; request(); } else play(item); }
    private void play(VideoMediaItem item) { command(VideoPackets.SessionAction.PLAY, item.key(), 0, mode(), generation(), "", -1, -1, "Play requested: " + item.title()); }
    private void queue(VideoMediaItem item) { command(VideoPackets.SessionAction.QUEUE, item.key(), 0, mode(), generation(), "", -1, -1, "Queue requested: " + item.title()); }
    private void removeQueue(int index) { command(VideoPackets.SessionAction.REMOVE_QUEUE, "", index, mode(), generation(), "", -1, -1); }
    private void seek(long delta) { command(VideoPackets.SessionAction.SEEK, "", Math.max(0, position() + delta), mode(), generation(), "", -1, -1); }
    private void cycleStream(VideoStreamOption.Kind kind) {
        VideoPackets.SessionState playback = state.session(controllerPos); if (playback == null || playback.item() == null) return;
        List<VideoStreamOption> options = new ArrayList<VideoStreamOption>(); for (VideoStreamOption option : playback.streams()) if (option.kind() == kind) options.add(option);
        if (options.isEmpty()) return; int current = kind == VideoStreamOption.Kind.AUDIO ? playback.selectedAudioStreamId() : playback.selectedSubtitleStreamId(); int next;
        if (kind == VideoStreamOption.Kind.SUBTITLE && current < 0) next = options.get(0).id();
        else { int index = -1; for (int value = 0; value < options.size(); value++) if (options.get(value).id() == current) { index = value; break; }
            next = index + 1 < options.size() ? options.get(index + 1).id() : kind == VideoStreamOption.Kind.SUBTITLE ? -1 : options.get(0).id(); }
        command(VideoPackets.SessionAction.SET_STREAMS, playback.item().key(), position(), playback.presentationMode(), playback.generation(), "",
                kind == VideoStreamOption.Kind.AUDIO ? next : playback.selectedAudioStreamId(), kind == VideoStreamOption.Kind.SUBTITLE ? next : playback.selectedSubtitleStreamId());
    }
    private void command(VideoPackets.SessionAction action, String item, long seek, PresentationMode mode, long generation, String session, int audio, int subtitle) {
        command(action,item,seek,mode,generation,session,audio,subtitle,"");
    }
    private void command(VideoPackets.SessionAction action, String item, long seek, PresentationMode mode, long generation, String session, int audio, int subtitle, String pending) {
        VideoPackets.SessionState current=state.session(controllerPos);boolean canControl=current==null||current.canControl();
        boolean dispatched=feedback.request(canControl,action==VideoPackets.SessionAction.TUNE,pending,
                ()->state.command(new VideoPackets.SessionCommand(action,controllerPos,libraryId,item,session,mode,generation,seek,audio,subtitle)));
        if(ProtocolLimits.videoProbeEnabled())Cinemarr.LOGGER.info("Acceptance video widget command: action={} dispatched={} canControl={}",action,dispatched,canControl);
        initGui();
    }
    void showError(String message){feedback.error(message);if(mc!=null)initGui();if(ProtocolLimits.videoProbeEnabled())Cinemarr.LOGGER.info("Acceptance video controller error displayed");}
    private void selectLibrary(String id) { libraryId = id; parents.clear(); parentKey = query = "";if(search!=null)search.setText(query); page = rowOffset = 0; request(); }
    private void request() { if (!libraryId.isEmpty()) {state.browse(libraryId, parentKey, query, page);initGui();} }
    private boolean paused() { VideoPackets.SessionState value = state.session(controllerPos); return value != null && value.paused(); }
    private long position() { VideoPackets.SessionState value = state.session(controllerPos); return value == null ? 0 : LegacyVideoPlayback.authoritativePositionMs(value,LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis())); }
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
        drawDefaultBackground(); drawCenteredString(fontRendererObj, "Cinemarr - Plex Video", width / 2, 6, 0xffffffff);
        VideoPackets.SessionState playback = state.session(controllerPos); String now = playback == null ? "No TV state" : playback.status().name().toLowerCase(java.util.Locale.ROOT)
                + (playback.item() == null ? "" : ": " + playback.item().title() + " " + time(position()) + "/" + time(playback.durationMs()));
        drawCenteredString(fontRendererObj, trim(now, width - 20), width / 2, 18, 0xffa0d8ff);
        super.drawScreen(mouseX, mouseY, partialTicks); if (search != null) search.drawTextBox(); if (sessionName != null) sessionName.drawTextBox();
        String notice=feedback.message();if (!notice.isEmpty()) drawCenteredString(fontRendererObj, trim(notice, width - 20), width / 2, layout.noticeY(), 0xffffb36b);
        for(java.util.Map.Entry<GuiButton,String> entry:tooltips.entrySet()){
            GuiButton button=entry.getKey();
            if(mouseX>=button.xPosition&&mouseX<button.xPosition+button.width&&mouseY>=button.yPosition&&mouseY<button.yPosition+button.height)
                drawHoveringText(fontRendererObj.listFormattedStringToWidth(entry.getValue(),Math.max(100,width-40)),mouseX,mouseY,fontRendererObj);
        }
        saveAcceptanceScreenshot();
    }
    private void inspectAcceptanceLayout() {
        if(!ProtocolLimits.videoProbeEnabled())return;
        List<Box> boxes=new ArrayList<Box>();
        for(Object value:buttonList)if(value instanceof GuiButton){GuiButton button=(GuiButton)value;boxes.add(new Box(button.xPosition,button.yPosition,button.width,button.height));}
        if(search!=null)boxes.add(layout.slot(Slot.SEARCH));
        if(sessionName!=null)boxes.add(layout.slot(Slot.SESSION));
        int clipped=0,overlaps=0;
        for(int i=0;i<boxes.size();i++){
            if(!boxes.get(i).fits(width,height))clipped++;
            for(int j=0;j<i;j++)if(boxes.get(i).overlaps(boxes.get(j)))overlaps++;
        }
        VideoPackets.SessionState playback=state.session(controllerPos);boolean control=playback!=null&&playback.canControl();
        Cinemarr.LOGGER.info("Acceptance video UI: width={} height={} widgets={} clipped={} canControl={} overlaps={}",width,height,boxes.size(),clipped,control,overlaps);
        acceptanceScreenshotPending=true;
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
