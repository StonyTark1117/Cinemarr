package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.ArrayDeque;
import java.util.Deque;

/** Controller-specific Plex movie/show browser and authoritative playback controls. */
public final class CinemarrVideoScreen extends Screen {
    private final long controllerPos;
    private final CinemarrVideoClientState state;
    private final Deque<String> parents=new ArrayDeque<>();
    private String libraryId="",parentKey="",query="",notice="";
    private EditBox search,sessionName;
    private int page,rowOffset;

    public CinemarrVideoScreen(long controllerPos,CinemarrVideoClientState state){
        super(Component.translatable("cinemarr.video.title"));this.controllerPos=controllerPos;this.state=state;
    }

    @Override protected void init(){
        clearWidgets();int panel=Math.min(760,width-16),left=(width-panel)/2,top=36;
        int libraryWidth=Math.max(80,panel/Math.max(1,state.libraries().libraries().size()));int x=left;
        for(VideoPackets.LibrarySummary library:state.libraries().libraries()){
            int actual=Math.min(libraryWidth,left+panel-x);Button button=Button.builder(Component.literal(library.displayName()),b->selectLibrary(library.id())).bounds(x,top,actual-2,20).build();
            button.active=!library.id().equals(libraryId);addRenderableWidget(button);x+=actual;
        }
        top+=26;
        search=addRenderableWidget(new EditBox(font,left,top,panel-190,20,Component.translatable("cinemarr.video.search")));
        search.setMaxLength(128);search.setValue(query);search.setHint(Component.translatable("cinemarr.video.search"));
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.screen.go"),b->{query=search.getValue().trim();page=0;request();}).bounds(left+panel-186,top,54,20).build());
        Button back=Button.builder(Component.translatable("cinemarr.video.back"),b->back()).bounds(left+panel-128,top,60,20).build();back.active=!parents.isEmpty();addRenderableWidget(back);
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.refresh"),b->request()).bounds(left+panel-64,top,64,20).build());
        top+=28;addRows(left,top,panel);
        addControls(left,panel);
        if(search!=null)setInitialFocus(search);
    }

    private void addRows(int left,int top,int panel){
        VideoPackets.BrowseResults results=state.browse();
        if(!results.libraryId().equals(libraryId)||!results.parentKey().equals(parentKey))return;
        int rows=Math.max(1,(height-top-82)/22);rowOffset=Math.max(0,Math.min(rowOffset,Math.max(0,results.items().size()-rows)));
        for(int row=0;row<rows&&row+rowOffset<results.items().size();row++){
            VideoMediaItem item=results.items().get(row+rowOffset);int y=top+row*22;
            String type=item.kind()==MediaKind.EPISODE?"E"+item.index()+" ":"";
            String duration=item.durationMs()>0?" ("+time(item.durationMs())+")":"";
            String label=type+item.title()+(item.parentTitle().isEmpty()?"":" — "+item.parentTitle())+duration;
            int actionWidth=item.kind()==MediaKind.MOVIE||item.kind()==MediaKind.EPISODE?54:68;
            Button titleButton=Button.builder(Component.literal(trim(label,panel-actionWidth-12)),b->activate(item)).bounds(left,y,panel-actionWidth-4,20).build();
            if(font.width(label)>panel-actionWidth-12)titleButton.setTooltip(Tooltip.create(Component.literal(label)));addRenderableWidget(titleButton);
            addRenderableWidget(Button.builder(Component.translatable(item.kind()==MediaKind.MOVIE||item.kind()==MediaKind.EPISODE?"cinemarr.video.play":"cinemarr.video.browse"),b->activate(item)).bounds(left+panel-actionWidth,y,actionWidth,20).build());
        }
    }

    private void addControls(int left,int panel){
        int y=height-50;VideoPackets.SessionState playback=state.session(controllerPos);long generation=playback==null?0:playback.generation();
        boolean paused=playback!=null&&playback.paused();
        addRenderableWidget(Button.builder(Component.translatable(paused?"cinemarr.screen.resume":"cinemarr.screen.pause"),b->command(paused?VideoPackets.SessionAction.RESUME:VideoPackets.SessionAction.PAUSE,"",playback==null?0:playback.positionMs(),mode(),generation)).bounds(left,y,70,20).build());
        addRenderableWidget(Button.builder(Component.literal("-30s"),b->seek(-30_000)).bounds(left+74,y,52,20).build());
        addRenderableWidget(Button.builder(Component.literal("+30s"),b->seek(30_000)).bounds(left+130,y,52,20).build());
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.stop"),b->command(VideoPackets.SessionAction.STOP,"",0,mode(),generation)).bounds(left+186,y,54,20).build());
        PresentationMode current=mode();int modeX=left+246;
        for(PresentationMode candidate:PresentationMode.values()){Button button=Button.builder(Component.literal(candidate.name().toLowerCase()),b->command(VideoPackets.SessionAction.SET_PRESENTATION,"",0,candidate,generation)).bounds(modeX,y,58,20).build();button.active=candidate!=current;addRenderableWidget(button);modeX+=62;}
        addRenderableWidget(Button.builder(Component.literal(CinemarrSettings.enabled()?"Screen on":"Screen off"),b->{CinemarrSettings.enabled(!CinemarrSettings.enabled());CinemarrSettings.saveEnabled();rebuildWidgets();}).bounds(left+panel-86,y,86,20).build());
        if(playback!=null&&playback.item()!=null){
            int streamY=y-24;Button audio=Button.builder(Component.literal("Audio: "+streamLabel(playback,VideoStreamOption.Kind.AUDIO,playback.selectedAudioStreamId(),"default")),b->cycleStream(playback,VideoStreamOption.Kind.AUDIO)).bounds(left,streamY,Math.min(180,panel/2-2),20).build();audio.active=playback.streams().stream().anyMatch(value->value.kind()==VideoStreamOption.Kind.AUDIO);addRenderableWidget(audio);
            Button subtitles=Button.builder(Component.literal("Subs: "+streamLabel(playback,VideoStreamOption.Kind.SUBTITLE,playback.selectedSubtitleStreamId(),"off")),b->cycleStream(playback,VideoStreamOption.Kind.SUBTITLE)).bounds(left+Math.min(184,panel/2+2),streamY,Math.min(180,panel/2-2),20).build();subtitles.active=playback.streams().stream().anyMatch(value->value.kind()==VideoStreamOption.Kind.SUBTITLE);addRenderableWidget(subtitles);
        }
        int sessionY=height-26;sessionName=addRenderableWidget(new EditBox(font,left,sessionY,180,20,Component.translatable("cinemarr.video.session")));sessionName.setMaxLength(64);sessionName.setHint(Component.translatable("cinemarr.video.session"));
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.video.tune"),b->command(VideoPackets.SessionAction.TUNE,"",0,mode(),generation,sessionName.getValue().trim())).bounds(left+184,sessionY,52,20).build());
    }

    private void activate(VideoMediaItem item){
        if(item.kind()==MediaKind.SHOW||item.kind()==MediaKind.SEASON){parents.push(parentKey);parentKey=item.key();query="";page=0;request();return;}
        VideoPackets.SessionState playback=state.session(controllerPos);command(VideoPackets.SessionAction.PLAY,item.key(),0,mode(),playback==null?0:playback.generation());notice="Starting "+item.title();rebuildWidgets();
    }
    private void back(){if(parents.isEmpty())return;parentKey=parents.pop();query="";page=0;request();}
    private void selectLibrary(String id){libraryId=id;parents.clear();parentKey="";query="";page=0;request();}
    private void request(){if(!libraryId.isEmpty())state.browse(libraryId,parentKey,query,page);}
    private void seek(long delta){VideoPackets.SessionState value=state.session(controllerPos);if(value==null)return;command(VideoPackets.SessionAction.SEEK,"",Math.max(0,value.positionMs()+delta),mode(),value.generation());}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation){command(action,item,seek,mode,generation,"");}
    private void command(VideoPackets.SessionAction action,String item,long seek,PresentationMode mode,long generation,String session){state.command(new VideoPackets.SessionCommand(action,controllerPos,libraryId,item,session,mode,generation,seek,-1,-1));}
    private void commandStreams(VideoPackets.SessionState playback,int audio,int subtitle){state.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_STREAMS,controllerPos,libraryId,playback.item().key(),"",playback.presentationMode(),playback.generation(),playback.positionMs(),audio,subtitle));}
    private void cycleStream(VideoPackets.SessionState playback,VideoStreamOption.Kind kind){java.util.List<VideoStreamOption> options=playback.streams().stream().filter(value->value.kind()==kind).toList();if(options.isEmpty())return;int current=kind==VideoStreamOption.Kind.AUDIO?playback.selectedAudioStreamId():playback.selectedSubtitleStreamId();int next;if(kind==VideoStreamOption.Kind.SUBTITLE&&current<0)next=options.getFirst().id();else{int index=-1;for(int i=0;i<options.size();i++)if(options.get(i).id()==current){index=i;break;}next=index+1<options.size()?options.get(index+1).id():(kind==VideoStreamOption.Kind.SUBTITLE?-1:options.getFirst().id());}commandStreams(playback,kind==VideoStreamOption.Kind.AUDIO?next:playback.selectedAudioStreamId(),kind==VideoStreamOption.Kind.SUBTITLE?next:playback.selectedSubtitleStreamId());}
    private static String streamLabel(VideoPackets.SessionState playback,VideoStreamOption.Kind kind,int id,String fallback){for(VideoStreamOption value:playback.streams())if(value.kind()==kind&&value.id()==id)return value.label();return fallback;}
    private PresentationMode mode(){VideoPackets.SessionState playback=state.session(controllerPos);return playback==null?PresentationMode.FIT:playback.presentationMode();}
    void stateChanged(){
        if(libraryId.isEmpty()&&!state.libraries().libraries().isEmpty()){libraryId=state.libraries().libraries().get(0).id();request();}
        if(minecraft!=null)rebuildWidgets();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257&&search!=null&&search.isFocused()){query=search.getValue().trim();page=0;request();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){if(scrollY!=0){rowOffset=Math.max(0,rowOffset+(scrollY<0?1:-1));rebuildWidgets();return true;}return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){renderBackground(graphics,mouseX,mouseY,partial);super.render(graphics,mouseX,mouseY,partial);graphics.drawCenteredString(font,title,width/2,10,0xffffff);VideoPackets.SessionState value=state.session(controllerPos);String now=value==null?"No TV state":value.status().name().toLowerCase()+(value.item()==null?"":": "+value.item().title()+"  "+time(value.positionMs())+"/"+time(value.durationMs()));graphics.drawCenteredString(font,trim(now,width-20),width/2,23,0xa0d8ff);if(!notice.isEmpty())graphics.drawCenteredString(font,trim(notice,width-20),width/2,height-64,0xffb36b);}
    private String trim(String value,int maximum){return font.width(value)<=maximum?value:font.plainSubstrByWidth(value,Math.max(0,maximum-font.width("…")))+"…";}
    private static String time(long ms){long total=Math.max(0,ms/1000);return String.format("%d:%02d",total/60,total%60);}
}
