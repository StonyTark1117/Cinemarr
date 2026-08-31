package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.ArrayDeque;
import java.util.Deque;

/** Controller-specific Plex movie/show browser and authoritative playback controls. */
public final class CinemarrVideoScreen extends Screen {
    private final long controllerPos;
    private final CinemarrVideoClientState state;
    private final Deque<String> parents=new ArrayDeque<>();
    private String libraryId="",parentKey="",query="",notice="",sessionDraft="";
    private EditBox search,sessionName;
    private int page,rowOffset;
    private boolean queueView;
    private boolean acceptanceScreenshotPending;

    public CinemarrVideoScreen(long controllerPos,CinemarrVideoClientState state){
        super(Component.translatable("cinemarr.video.title"));this.controllerPos=controllerPos;this.state=state;
    }

    @Override protected void init(){
        if(sessionName!=null)sessionDraft=sessionName.getValue();
        clearWidgets();int panel=Math.min(760,width-16),left=(width-panel)/2,top=36;
        int libraryWidth=Math.max(80,panel/Math.max(1,state.libraries().libraries().size()));int x=left;
        for(VideoPackets.LibrarySummary library:state.libraries().libraries()){
            int actual=Math.min(libraryWidth,left+panel-x);Button button=Button.builder(Component.literal(library.displayName()),b->selectLibrary(library.id())).bounds(x,top,actual-2,20).build();
            button.active=!library.id().equals(libraryId);addRenderableWidget(button);x+=actual;
        }
        top+=26;
        search=addRenderableWidget(new EditBox(font,left,top,panel-266,20,Component.translatable("cinemarr.video.search")));
        search.setMaxLength(128);search.setValue(query);search.setHint(Component.translatable("cinemarr.video.search"));
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.go"),b->{queueView=false;query=search.getValue().trim();page=0;request();}).bounds(left+panel-262,top,54,20).build());
        Button back=Button.builder(Component.translatable("cinemarr.video.back"),b->back()).bounds(left+panel-204,top,60,20).build();back.active=!parents.isEmpty()&&!queueView;addRenderableWidget(back);
        addRenderableWidget(Button.builder(queueView?Component.literal("Clear"):Component.translatable("cinemarr.video.refresh"),b->{if(queueView)clearQueue();else request();}).bounds(left+panel-140,top,64,20).build());
        addRenderableWidget(Button.builder(Component.literal(queueView?"Browse":"Queue"),b->{queueView=!queueView;rowOffset=0;rebuildWidgets();}).bounds(left+panel-72,top,72,20).build());
        top+=28;addRows(left,top,panel);
        addControls(left,panel);
        if(search!=null)setInitialFocus(search);
        inspectAcceptanceLayout();
    }

    private void addRows(int left,int top,int panel){
        if(queueView){addQueueRows(left,top,panel);return;}
        VideoPackets.BrowseResults results=state.browse();
        if(!results.libraryId().equals(libraryId)||!results.parentKey().equals(parentKey))return;
        int rows=Math.max(1,(height-top-104)/22);rowOffset=Math.max(0,Math.min(rowOffset,Math.max(0,results.items().size()-rows)));
        for(int row=0;row<rows&&row+rowOffset<results.items().size();row++){
            VideoMediaItem item=results.items().get(row+rowOffset);int y=top+row*22;
            String type=item.kind()==MediaKind.EPISODE?"E"+item.index()+" ":"";
            String duration=item.durationMs()>0?" ("+time(item.durationMs())+")":"";
            String label=type+item.title()+(item.parentTitle().isEmpty()?"":" — "+item.parentTitle())+duration;
            int actionWidth=item.kind()==MediaKind.MOVIE||item.kind()==MediaKind.EPISODE?108:68;
            Button titleButton=Button.builder(Component.literal(trim(label,panel-actionWidth-12)),b->activate(item)).bounds(left,y,panel-actionWidth-4,20).build();
            if(font.width(label)>panel-actionWidth-12)titleButton.setTooltip(Tooltip.create(Component.literal(label)));addRenderableWidget(titleButton);
            if(item.kind()==MediaKind.MOVIE||item.kind()==MediaKind.EPISODE){addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.play"),b->activate(item)).bounds(left+panel-actionWidth,y,52,20).build());addRenderableWidget(Button.builder(Component.literal("+ Queue"),b->queue(item)).bounds(left+panel-52,y,52,20).build());}
            else addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.browse"),b->activate(item)).bounds(left+panel-actionWidth,y,actionWidth,20).build());
        }
        int pagerY=top+rows*22;Button previous=Button.builder(Component.literal("< Prev"),b->{page=Math.max(0,page-1);rowOffset=0;request();}).bounds(left,pagerY,60,20).build();previous.active=page>0;addRenderableWidget(previous);addRenderableWidget(Button.builder(Component.literal("Page "+(page+1)),b->{}).bounds(left+64,pagerY,72,20).build());Button next=Button.builder(Component.literal("Next >"),b->{page++;rowOffset=0;request();}).bounds(left+140,pagerY,60,20).build();next.active=results.hasMore();addRenderableWidget(next);
    }

    private void addQueueRows(int left,int top,int panel){java.util.List<QueuedVideo> queue=state.queue(controllerPos);int rows=Math.max(1,(height-top-82)/22);rowOffset=Math.max(0,Math.min(rowOffset,Math.max(0,queue.size()-rows)));for(int row=0;row<rows&&row+rowOffset<queue.size();row++){int index=row+rowOffset;QueuedVideo entry=queue.get(index);String label=(index+1)+". "+entry.item().title()+(entry.item().parentTitle().isEmpty()?"":" — "+entry.item().parentTitle());int y=top+row*22;addRenderableWidget(Button.builder(Component.literal(trim(label,panel-70)),b->{}).bounds(left,y,panel-64,20).build());addRenderableWidget(Button.builder(Component.literal("Remove"),b->removeQueue(index)).bounds(left+panel-60,y,60,20).build());}}

    private void addControls(int left,int panel){
        int y=height-50;VideoPackets.SessionState playback=state.session(controllerPos);long generation=playback==null?0:playback.generation();
        boolean paused=playback!=null&&playback.paused();
        addRenderableWidget(Button.builder(Component.translatable(paused?"cinemarr.screen.resume":"cinemarr.screen.pause"),b->command(paused?VideoPackets.SessionAction.RESUME:VideoPackets.SessionAction.PAUSE,"",playback==null?0:playback.positionMs(),mode(),generation)).bounds(left,y,70,20).build());
        addRenderableWidget(Button.builder(Component.literal("-30s"),b->seek(-30_000)).bounds(left+74,y,52,20).build());
        addRenderableWidget(Button.builder(Component.literal("+30s"),b->seek(30_000)).bounds(left+130,y,52,20).build());
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.stop"),b->command(VideoPackets.SessionAction.STOP,"",0,mode(),generation)).bounds(left+186,y,54,20).build());
        addRenderableWidget(Button.builder(Component.literal("Skip"),b->command(VideoPackets.SessionAction.SKIP,"",0,mode(),generation)).bounds(left+244,y,48,20).build());
        if(playback!=null&&playback.item()!=null&&playback.item().kind()==MediaKind.EPISODE)addRenderableWidget(Button.builder(Component.literal("Next Ep"),b->command(VideoPackets.SessionAction.CONTINUE_EPISODE,"",0,mode(),generation)).bounds(left+panel-160,y,70,20).build());
        PresentationMode current=mode();int modeX=left+296;
        for(PresentationMode candidate:PresentationMode.values()){Button button=Button.builder(Component.literal(candidate.name().toLowerCase()),b->command(VideoPackets.SessionAction.SET_PRESENTATION,"",0,candidate,generation)).bounds(modeX,y,58,20).build();button.active=candidate!=current;addRenderableWidget(button);modeX+=62;}
        addRenderableWidget(Button.builder(Component.literal(CinemarrSettings.enabled()?"Screen on":"Screen off"),b->{CinemarrSettings.enabled(!CinemarrSettings.enabled());CinemarrSettings.saveEnabled();rebuildWidgets();}).bounds(left+panel-86,y,86,20).build());
        if(playback!=null&&playback.item()!=null){
            int streamY=y-24;Button audio=Button.builder(Component.literal("Audio: "+streamLabel(playback,VideoStreamOption.Kind.AUDIO,playback.selectedAudioStreamId(),"default")),b->cycleStream(playback,VideoStreamOption.Kind.AUDIO)).bounds(left,streamY,Math.min(180,panel/2-2),20).build();audio.active=playback.streams().stream().anyMatch(value->value.kind()==VideoStreamOption.Kind.AUDIO);addRenderableWidget(audio);
            Button subtitles=Button.builder(Component.literal("Subs: "+streamLabel(playback,VideoStreamOption.Kind.SUBTITLE,playback.selectedSubtitleStreamId(),"off")),b->cycleStream(playback,VideoStreamOption.Kind.SUBTITLE)).bounds(left+Math.min(184,panel/2+2),streamY,Math.min(180,panel/2-2),20).build();subtitles.active=playback.streams().stream().anyMatch(value->value.kind()==VideoStreamOption.Kind.SUBTITLE);addRenderableWidget(subtitles);
        }
        int sessionY=height-26;sessionName=addRenderableWidget(new EditBox(font,left,sessionY,180,20,Component.translatable("cinemarr.video.session")));sessionName.setMaxLength(64);sessionName.setHint(Component.translatable("cinemarr.video.session"));sessionName.setValue(sessionDraft);
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.tune"),b->command(VideoPackets.SessionAction.TUNE,"",0,mode(),generation,sessionName.getValue().trim())).bounds(left+184,sessionY,52,20).build());
        addRenderableWidget(Button.builder(Component.literal("Vol -"),b->volume(-0.1)).bounds(left+244,sessionY,52,20).build());
        addRenderableWidget(Button.builder(Component.literal((int)Math.round(CinemarrSettings.volume()*100)+"%"),b->{}).bounds(left+300,sessionY,52,20).build());
        addRenderableWidget(Button.builder(Component.literal("Vol +"),b->volume(0.1)).bounds(left+356,sessionY,52,20).build());
    }

    private void activate(VideoMediaItem item){
        if(item.kind()==MediaKind.SHOW||item.kind()==MediaKind.SEASON){parents.push(parentKey);parentKey=item.key();query="";page=0;request();return;}
        VideoPackets.SessionState playback=state.session(controllerPos);command(VideoPackets.SessionAction.PLAY,item.key(),0,mode(),playback==null?0:playback.generation());notice="Starting "+item.title();rebuildWidgets();
    }
    private void queue(VideoMediaItem item){VideoPackets.SessionState playback=state.session(controllerPos);command(VideoPackets.SessionAction.QUEUE,item.key(),0,mode(),playback==null?0:playback.generation());notice="Queued "+item.title();rebuildWidgets();}
    private void removeQueue(int index){VideoPackets.SessionState playback=state.session(controllerPos);if(playback!=null)command(VideoPackets.SessionAction.REMOVE_QUEUE,"",index,mode(),playback.generation());}
    private void clearQueue(){VideoPackets.SessionState playback=state.session(controllerPos);if(playback!=null)command(VideoPackets.SessionAction.CLEAR_QUEUE,"",0,mode(),playback.generation());}
    private void volume(double delta){CinemarrSettings.volume(Math.max(0,Math.min(1,CinemarrSettings.volume()+delta)));CinemarrSettings.saveVolume();rebuildWidgets();}
    private void back(){if(parents.isEmpty())return;parentKey=parents.pop();query="";page=0;request();}
    private void selectLibrary(String id){libraryId=id;parents.clear();parentKey="";query="";page=0;request();}
    private void request(){if(!libraryId.isEmpty())state.browse(libraryId,parentKey,query,page);}
    private void seek(long delta){VideoPackets.SessionState value=state.session(controllerPos);if(value==null)return;command(VideoPackets.SessionAction.SEEK,"",Math.max(0,value.positionMs()+delta),mode(),value.generation());}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation){command(action,item,seek,mode,generation,"");}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation,String session){VideoPackets.SessionState current=state.session(controllerPos);if(current!=null&&!current.canControl()&&action!=VideoPackets.SessionAction.TUNE){notice="You do not have permission to control this TV";rebuildWidgets();return;}sessionDraft=session;state.command(new VideoPackets.SessionCommand(action,controllerPos,libraryId,item,session,mode,generation,seek,-1,-1));}
    private void commandStreams(VideoPackets.SessionState playback,int audio,int subtitle){state.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_STREAMS,controllerPos,libraryId,playback.item().key(),"",playback.presentationMode(),playback.generation(),playback.positionMs(),audio,subtitle));}
    private void cycleStream(VideoPackets.SessionState playback,VideoStreamOption.Kind kind){java.util.List<VideoStreamOption> options=playback.streams().stream().filter(value->value.kind()==kind).toList();if(options.isEmpty())return;int current=kind==VideoStreamOption.Kind.AUDIO?playback.selectedAudioStreamId():playback.selectedSubtitleStreamId();int next;if(kind==VideoStreamOption.Kind.SUBTITLE&&current<0)next=options.get(0).id();else{int index=-1;for(int i=0;i<options.size();i++)if(options.get(i).id()==current){index=i;break;}next=index+1<options.size()?options.get(index+1).id():(kind==VideoStreamOption.Kind.SUBTITLE?-1:options.get(0).id());}commandStreams(playback,kind==VideoStreamOption.Kind.AUDIO?next:playback.selectedAudioStreamId(),kind==VideoStreamOption.Kind.SUBTITLE?next:playback.selectedSubtitleStreamId());}
    private static String streamLabel(VideoPackets.SessionState playback,VideoStreamOption.Kind kind,int id,String fallback){for(VideoStreamOption value:playback.streams())if(value.kind()==kind&&value.id()==id)return value.label();return fallback;}
    private PresentationMode mode(){VideoPackets.SessionState playback=state.session(controllerPos);return playback==null?PresentationMode.FIT:playback.presentationMode();}
    void stateChanged(){
        if(libraryId.isEmpty()&&!state.libraries().libraries().isEmpty()){libraryId=state.libraries().libraries().get(0).id();request();}
        VideoPackets.SessionState current=state.session(controllerPos);if(current!=null&&!current.message().isBlank())notice=current.message();
        if(minecraft!=null)rebuildWidgets();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257&&search!=null&&search.isFocused()){query=search.getValue().trim();page=0;request();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollY){if(scrollY!=0){rowOffset=Math.max(0,rowOffset+(scrollY<0?1:-1));rebuildWidgets();return true;}return super.mouseScrolled(mouseX,mouseY,scrollY);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){renderBackground(graphics);super.render(graphics,mouseX,mouseY,partial);graphics.drawCenteredString(font,title,width/2,10,0xffffff);VideoPackets.SessionState value=state.session(controllerPos);String now=value==null?"No TV state":value.status().name().toLowerCase()+(value.item()==null?"":": "+value.item().title()+"  "+time(value.positionMs())+"/"+time(value.durationMs()));graphics.drawCenteredString(font,trim(now,width-20),width/2,23,0xa0d8ff);if(!notice.isEmpty())graphics.drawCenteredString(font,trim(notice,width-20),width/2,height-64,0xffb36b);saveAcceptanceScreenshot();}
    private void inspectAcceptanceLayout(){if(!ProtocolLimits.videoProbeEnabled())return;int widgets=0,clipped=0;for(net.minecraft.client.gui.components.events.GuiEventListener child:children())if(child instanceof AbstractWidget widget){widgets++;if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>width||widget.getY()+widget.getHeight()>height)clipped++;}VideoPackets.SessionState playback=state.session(controllerPos);boolean control=playback!=null&&playback.canControl();Cinemarr.LOGGER.info("Acceptance video UI: width={} height={} widgets={} clipped={} canControl={}",width,height,widgets,clipped,control);acceptanceScreenshotPending=true;}
    private void saveAcceptanceScreenshot(){if(!acceptanceScreenshotPending||minecraft==null)return;acceptanceScreenshotPending=false;Screenshot.grab(minecraft.gameDirectory,"cinemarr-video-ui-acceptance.png",minecraft.getMainRenderTarget(),message->Cinemarr.LOGGER.info("Acceptance video UI screenshot: {}",message.getString()));}
    private String trim(String value,int maximum){return font.width(value)<=maximum?value:font.plainSubstrByWidth(value,Math.max(0,maximum-font.width("…")))+"…";}
    private static String time(long ms){long total=Math.max(0,ms/1000);return String.format("%d:%02d",total/60,total%60);}
}
