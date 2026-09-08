package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.client.VideoControllerLayout;
import stonytark.cinemarr.core.client.VideoControllerFeedback;
import stonytark.cinemarr.core.client.VideoControllerLayout.Box;
import stonytark.cinemarr.core.client.VideoControllerLayout.Slot;
import stonytark.cinemarr.Cinemarr;

import java.util.ArrayDeque;
import java.util.Deque;

/** Controller-specific Plex movie/show browser and authoritative playback controls. */
public final class CinemarrVideoScreen extends Screen {
    private final long controllerPos;
    private final CinemarrVideoClientState state;
    private final Deque<String> parents=new ArrayDeque<>();
    private String libraryId="",parentKey="",query="",sessionDraft="";
    private final VideoControllerFeedback feedback=new VideoControllerFeedback();
    private EditBox search,sessionName;
    private int page,rowOffset,libraryPage;
    private VideoControllerLayout layout;
    private final java.util.Map<String,AbstractWidget> focusTargets=new java.util.LinkedHashMap<>();
    private boolean queueView;
    private boolean acceptanceScreenshotPending;

    public CinemarrVideoScreen(long controllerPos,CinemarrVideoClientState state){
        super(Component.translatable("cinemarr.video.title"));this.controllerPos=controllerPos;this.state=state;
    }

    @Override protected void init(){
        VideoPackets.SessionState authoritative=state.session(controllerPos);
        feedback.updateServerMessage(authoritative==null?"":authoritative.message());
        var focused=getFocused(); boolean first=search==null;
        clearWidgets();focusTargets.clear();layout=new VideoControllerLayout(width,height);
        var libraries=state.libraries().libraries();
        libraryPage=Math.max(0,Math.min(libraryPage,layout.libraryPages(libraries.size())-1));
        control(Slot.LIBRARY_PREVIOUS,Component.literal("<"),b->{libraryPage--;rebuildWidgets();}).active=libraryPage>0;
        control(Slot.LIBRARY_NEXT,Component.literal(">"),b->{libraryPage++;rebuildWidgets();}).active=libraryPage+1<layout.libraryPages(libraries.size());
        for(int slot=0;slot<layout.libraryCapacity();slot++){
            int index=libraryPage*layout.libraryCapacity()+slot;if(index>=libraries.size())break;
            VideoPackets.LibrarySummary library=libraries.get(index);Box box=layout.library(slot);
            Button button=widget("library:"+library.id(),Button.builder(Component.literal(trim(library.displayName(),box.width-8)),b->selectLibrary(library.id())).bounds(box.x,box.y,box.width,box.height).build());
            button.setTooltip(Tooltip.create(Component.literal(library.displayName())));button.active=!library.id().equals(libraryId);
        }
        search=edit(search,Slot.SEARCH,"cinemarr.video.search",128,query);
        control(Slot.GO,Component.translatable("cinemarr.screen.go"),b->{queueView=false;query=search.getValue().trim();page=rowOffset=0;request();});
        control(Slot.BACK,Component.translatable("cinemarr.video.back"),b->back()).active=!parents.isEmpty()&&!queueView;
        control(Slot.REFRESH,queueView?Component.literal("Clear"):Component.translatable("cinemarr.video.refresh"),b->{if(queueView)clearQueue();else request();});
        control(Slot.QUEUE,Component.literal(queueView?"Browse":"Queue"),b->{queueView=!queueView;rowOffset=0;rebuildWidgets();});
        addRows(layout.left(),layout.contentTop(),layout.panel());
        addControls();
        if(first)setInitialFocus(search);else if(focused==search||focused==sessionName)setFocused(focused);
        inspectAcceptanceLayout();
    }

    private Button control(Slot slot,Component label,Button.OnPress action){
        Box box=layout.slot(slot);Button button=widget(slot.name(),Button.builder(Component.literal(trim(label.getString(),box.width-8)),action).bounds(box.x,box.y,box.width,box.height).build());
        button.setTooltip(Tooltip.create(label));return button;
    }
    private <T extends AbstractWidget> T widget(String id,T widget){focusTargets.put(id,widget);return addRenderableWidget(widget);}
    @Override protected void rebuildWidgets(){
        // Screen clears focus BEFORE init and chooses a new default AFTER it.
        // Preserve a semantic target around that whole lifecycle, including resize.
        String target=null;for(var entry:focusTargets.entrySet())if(entry.getValue()==getFocused()){target=entry.getKey();break;}
        super.rebuildWidgets();setFocused(target==null?null:focusTargets.get(target));
    }
    private EditBox edit(EditBox field,Slot slot,String translation,int maximum,String initial){
        Box box=layout.slot(slot);
        // Keep the actual widget: updates and resizes must preserve draft, cursor,
        // selection, horizontal scroll and focus, not just the submitted query.
        if(field==null){field=new EditBox(font,box.x,box.y,box.width,box.height,Component.translatable(translation));field.setMaxLength(maximum);field.setValue(initial);}
        else{field.setX(box.x);field.setY(box.y);field.setWidth(box.width);}
        field.setHint(Component.literal(trim(Component.translatable(translation).getString(),box.width-8)));
        return widget(slot.name(),field);
    }

    private void addRows(int left,int top,int panel){
        if(queueView){addQueueRows(left,top,panel);return;}
        VideoPackets.BrowseResults results=state.browse();
        if(!results.libraryId().equals(libraryId)||!results.parentKey().equals(parentKey)||!results.query().equals(query)||results.page()!=page)return;
        int rows=layout.rows(false);rowOffset=Math.max(0,Math.min(rowOffset,Math.max(0,results.items().size()-rows)));
        for(int row=0;row<rows&&row+rowOffset<results.items().size();row++){
            VideoMediaItem item=results.items().get(row+rowOffset);int y=top+row*22;
            String type=item.kind()==MediaKind.EPISODE?"E"+item.index()+" ":"";
            String duration=item.durationMs()>0?" ("+time(item.durationMs())+")":"";
            String label=type+item.title()+(item.parentTitle().isEmpty()?"":" — "+item.parentTitle())+duration;
            int actionWidth=item.kind()==MediaKind.MOVIE||item.kind()==MediaKind.EPISODE?108:68;
            Button titleButton=Button.builder(Component.literal(trim(label,panel-actionWidth-12)),b->activate(item)).bounds(left,y,panel-actionWidth-4,20).build();
            if(font.width(label)>panel-actionWidth-12)titleButton.setTooltip(Tooltip.create(Component.literal(label)));widget("item:"+item.key(),titleButton);
            if(item.kind()==MediaKind.MOVIE||item.kind()==MediaKind.EPISODE){widget("play:"+item.key(),Button.builder(Component.translatable("cinemarr.video.play"),b->activate(item)).bounds(left+panel-actionWidth,y,52,20).build());widget("queue:"+item.key(),Button.builder(Component.literal("+ Queue"),b->queue(item)).bounds(left+panel-52,y,52,20).build());}
            else widget("browse:"+item.key(),Button.builder(Component.translatable("cinemarr.video.browse"),b->activate(item)).bounds(left+panel-actionWidth,y,actionWidth,20).build());
        }
        int pagerY=layout.pagerY();Button previous=Button.builder(Component.literal("< Prev"),b->{page=Math.max(0,page-1);rowOffset=0;request();}).bounds(left,pagerY,60,20).build();previous.active=page>0;widget("previous-page",previous);widget("page",Button.builder(Component.literal("Page "+(page+1)),b->{}).bounds(left+64,pagerY,72,20).build()).active=false;Button next=Button.builder(Component.literal("Next >"),b->{page++;rowOffset=0;request();}).bounds(left+140,pagerY,60,20).build();next.active=results.hasMore();widget("next-page",next);
    }

    private void addQueueRows(int left,int top,int panel){java.util.List<QueuedVideo> queue=state.queue(controllerPos);int rows=layout.rows(true);rowOffset=Math.max(0,Math.min(rowOffset,Math.max(0,queue.size()-rows)));for(int row=0;row<rows&&row+rowOffset<queue.size();row++){int index=row+rowOffset;QueuedVideo entry=queue.get(index);String label=(index+1)+". "+entry.item().title()+(entry.item().parentTitle().isEmpty()?"":" — "+entry.item().parentTitle());int y=top+row*22;widget("queued:"+index,Button.builder(Component.literal(trim(label,panel-70)),b->{}).bounds(left,y,panel-64,20).build()).active=false;widget("remove:"+index,Button.builder(Component.literal("Remove"),b->removeQueue(index)).bounds(left+panel-60,y,60,20).build());}}

    private void addControls(){
        VideoPackets.SessionState playback=state.session(controllerPos);long generation=playback==null?0:playback.generation();
        boolean paused=playback!=null&&playback.paused();
        control(Slot.PAUSE,Component.translatable(paused?"cinemarr.screen.resume":"cinemarr.screen.pause"),b->command(paused?VideoPackets.SessionAction.RESUME:VideoPackets.SessionAction.PAUSE,"",playback==null?0:CinemarrVideoPlayback.authoritativePositionMsLocal(playback),mode(),generation));
        control(Slot.SEEK_BACK,Component.literal("-30s"),b->seek(-30_000));
        control(Slot.SEEK_FORWARD,Component.literal("+30s"),b->seek(30_000));
        control(Slot.STOP,Component.translatable("cinemarr.video.stop"),b->command(VideoPackets.SessionAction.STOP,"",0,mode(),generation));
        control(Slot.SKIP,Component.literal("Skip"),b->command(VideoPackets.SessionAction.SKIP,"",0,mode(),generation));
        PresentationMode current=mode();
        for(PresentationMode candidate:PresentationMode.values())control(Slot.valueOf(candidate.name()),Component.literal(candidate.name().toLowerCase(java.util.Locale.ROOT)),b->command(VideoPackets.SessionAction.SET_PRESENTATION,"",0,candidate,generation)).active=candidate!=current;
        control(Slot.SCREEN,Component.literal(CinemarrSettings.enabled()?"Screen on":"Screen off"),b->{CinemarrSettings.enabled(!CinemarrSettings.enabled());CinemarrSettings.saveEnabled();rebuildWidgets();});
        if(playback!=null&&playback.item()!=null){
            control(Slot.AUDIO,Component.literal("Audio: "+streamLabel(playback,VideoStreamOption.Kind.AUDIO,playback.selectedAudioStreamId(),"default")),b->cycleStream(playback,VideoStreamOption.Kind.AUDIO)).active=playback.streams().stream().anyMatch(value->value.kind()==VideoStreamOption.Kind.AUDIO);
            control(Slot.SUBTITLES,Component.literal("Subs: "+streamLabel(playback,VideoStreamOption.Kind.SUBTITLE,playback.selectedSubtitleStreamId(),"off")),b->cycleStream(playback,VideoStreamOption.Kind.SUBTITLE)).active=playback.streams().stream().anyMatch(value->value.kind()==VideoStreamOption.Kind.SUBTITLE);
            if(playback.item().kind()==MediaKind.EPISODE)control(Slot.CONTINUE,Component.literal("Next Ep"),b->command(VideoPackets.SessionAction.CONTINUE_EPISODE,"",0,mode(),generation));
        }
        sessionName=edit(sessionName,Slot.SESSION,"cinemarr.video.session",64,sessionDraft);
        control(Slot.TUNE,Component.translatable("cinemarr.video.tune"),b->command(VideoPackets.SessionAction.TUNE,"",0,mode(),generation,sessionName.getValue().trim()));
        control(Slot.VOLUME_DOWN,Component.literal("Vol -"),b->volume(-0.1));
        control(Slot.VOLUME,Component.literal((int)Math.round(CinemarrSettings.volume()*100)+"%"),b->{}).active=false;
        control(Slot.VOLUME_UP,Component.literal("Vol +"),b->volume(0.1));
    }

    private void activate(VideoMediaItem item){
        if(item.kind()==MediaKind.SHOW||item.kind()==MediaKind.SEASON){parents.push(parentKey);parentKey=item.key();query="";if(search!=null)search.setValue(query);page=rowOffset=0;request();return;}
        VideoPackets.SessionState playback=state.session(controllerPos);command(VideoPackets.SessionAction.PLAY,item.key(),0,mode(),playback==null?0:playback.generation(),"","Play requested: "+item.title());
    }
    private void queue(VideoMediaItem item){VideoPackets.SessionState playback=state.session(controllerPos);command(VideoPackets.SessionAction.QUEUE,item.key(),0,mode(),playback==null?0:playback.generation(),"","Queue requested: "+item.title());}
    private void removeQueue(int index){VideoPackets.SessionState playback=state.session(controllerPos);if(playback!=null)command(VideoPackets.SessionAction.REMOVE_QUEUE,"",index,mode(),playback.generation());}
    private void clearQueue(){VideoPackets.SessionState playback=state.session(controllerPos);if(playback!=null)command(VideoPackets.SessionAction.CLEAR_QUEUE,"",0,mode(),playback.generation());}
    private void volume(double delta){CinemarrSettings.volume(Math.max(0,Math.min(1,CinemarrSettings.volume()+delta)));CinemarrSettings.saveVolume();rebuildWidgets();}
    private void back(){if(parents.isEmpty())return;parentKey=parents.pop();query="";if(search!=null)search.setValue(query);page=rowOffset=0;request();}
    private void selectLibrary(String id){libraryId=id;parents.clear();parentKey="";query="";if(search!=null)search.setValue(query);page=rowOffset=0;request();}
    private void request(){if(!libraryId.isEmpty()){state.browse(libraryId,parentKey,query,page);rebuildWidgets();}}
    private void seek(long delta){VideoPackets.SessionState value=state.session(controllerPos);if(value==null)return;command(VideoPackets.SessionAction.SEEK,"",Math.max(0,CinemarrVideoPlayback.authoritativePositionMsLocal(value)+delta),mode(),value.generation());}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation){command(action,item,seek,mode,generation,"");}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation,String session){command(action,item,seek,mode,generation,session,"");}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation,String session,String pending){
        if(send(new VideoPackets.SessionCommand(action,controllerPos,libraryId,item,session,mode,generation,seek,-1,-1),pending)&&action==VideoPackets.SessionAction.TUNE)sessionDraft=session;
    }
    private void commandStreams(VideoPackets.SessionState playback,int audio,int subtitle){send(new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_STREAMS,controllerPos,libraryId,playback.item().key(),"",playback.presentationMode(),playback.generation(),CinemarrVideoPlayback.authoritativePositionMsLocal(playback),audio,subtitle),"");}
    private boolean send(VideoPackets.SessionCommand command,String pending){
        VideoPackets.SessionState current=state.session(controllerPos);boolean canControl=current==null||current.canControl();
        boolean dispatched=feedback.request(canControl,command.action()==VideoPackets.SessionAction.TUNE,pending,()->state.command(command));
        if(ProtocolLimits.videoProbeEnabled())Cinemarr.LOGGER.info("Acceptance video widget command: action={} dispatched={} canControl={}",command.action(),dispatched,canControl);
        rebuildWidgets();return dispatched;
    }
    void showError(String message){feedback.error(message);if(minecraft!=null)rebuildWidgets();if(ProtocolLimits.videoProbeEnabled())Cinemarr.LOGGER.info("Acceptance video controller error displayed");}
    private void cycleStream(VideoPackets.SessionState playback,VideoStreamOption.Kind kind){java.util.List<VideoStreamOption> options=playback.streams().stream().filter(value->value.kind()==kind).toList();if(options.isEmpty())return;int current=kind==VideoStreamOption.Kind.AUDIO?playback.selectedAudioStreamId():playback.selectedSubtitleStreamId();int next;if(kind==VideoStreamOption.Kind.SUBTITLE&&current<0)next=options.get(0).id();else{int index=-1;for(int i=0;i<options.size();i++)if(options.get(i).id()==current){index=i;break;}next=index+1<options.size()?options.get(index+1).id():(kind==VideoStreamOption.Kind.SUBTITLE?-1:options.get(0).id());}commandStreams(playback,kind==VideoStreamOption.Kind.AUDIO?next:playback.selectedAudioStreamId(),kind==VideoStreamOption.Kind.SUBTITLE?next:playback.selectedSubtitleStreamId());}
    private static String streamLabel(VideoPackets.SessionState playback,VideoStreamOption.Kind kind,int id,String fallback){for(VideoStreamOption value:playback.streams())if(value.kind()==kind&&value.id()==id)return value.label();return fallback;}
    private PresentationMode mode(){VideoPackets.SessionState playback=state.session(controllerPos);return playback==null?PresentationMode.FIT:playback.presentationMode();}
    void stateChanged(){
        if(libraryId.isEmpty()&&!state.libraries().libraries().isEmpty()){libraryId=state.libraries().libraries().get(0).id();request();}
        if(minecraft!=null)rebuildWidgets();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257&&search!=null&&search.isFocused()){query=search.getValue().trim();page=rowOffset=0;request();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){if(scrollY!=0){rowOffset=Math.max(0,rowOffset+(scrollY<0?1:-1));rebuildWidgets();return true;}return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){renderBackground(graphics,mouseX,mouseY,partial);super.render(graphics,mouseX,mouseY,partial);graphics.drawCenteredString(font,title,width/2,6,0xffffffff);VideoPackets.SessionState value=state.session(controllerPos);String now=value==null?"No TV state":value.status().name().toLowerCase()+(value.item()==null?"":": "+value.item().title()+"  "+time(CinemarrVideoPlayback.authoritativePositionMsLocal(value))+"/"+time(value.durationMs()));graphics.drawCenteredString(font,trim(now,width-20),width/2,18,0xffa0d8ff);String notice=feedback.message();if(!notice.isEmpty())graphics.drawCenteredString(font,trim(notice,width-20),width/2,layout.noticeY(),0xffffb36b);saveAcceptanceScreenshot();}
    private void inspectAcceptanceLayout(){
        if(!ProtocolLimits.videoProbeEnabled())return;
        java.util.List<Box> boxes=new java.util.ArrayList<>();int clipped=0,overlaps=0;
        for(net.minecraft.client.gui.components.events.GuiEventListener child:children())if(child instanceof AbstractWidget widget){
            Box box=new Box(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight());
            if(!box.fits(width,height))clipped++;
            for(Box previous:boxes)if(box.overlaps(previous))overlaps++;
            boxes.add(box);
        }
        VideoPackets.SessionState playback=state.session(controllerPos);boolean control=playback!=null&&playback.canControl();
        Cinemarr.LOGGER.info("Acceptance video UI: width={} height={} widgets={} clipped={} canControl={} overlaps={}",width,height,boxes.size(),clipped,control,overlaps);
        acceptanceScreenshotPending=true;
    }
    private void saveAcceptanceScreenshot(){if(!acceptanceScreenshotPending||minecraft==null)return;acceptanceScreenshotPending=false;Screenshot.grab(minecraft.gameDirectory,"cinemarr-video-ui-acceptance.png",minecraft.getMainRenderTarget(),message->Cinemarr.LOGGER.info("Acceptance video UI screenshot: {}",message.getString()));}
    private String trim(String value,int maximum){return font.width(value)<=maximum?value:font.plainSubstrByWidth(value,Math.max(0,maximum-font.width("…")))+"…";}
    private static String time(long ms){long total=Math.max(0,ms/1000);return String.format("%d:%02d",total/60,total%60);}
}
