package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.DisplaySettingsDraft;
import stonytark.cinemarr.core.video.PixelMapping;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.ResolutionChoice;
import stonytark.cinemarr.core.video.TvDisplaySettings;

/** Display settings page for the 1.20.2 GUI API. */
public final class DisplaySettingsScreen extends Screen {
    private final long controllerPos; private final CinemarrVideoClientState state; private final CinemarrVideoScreen parent;
    private DisplaySettingsDraft draft; private EditBox widthBox,heightBox; private String error="";
    public DisplaySettingsScreen(long pos,CinemarrVideoClientState state,CinemarrVideoScreen parent){super(Component.literal("Display Settings"));controllerPos=pos;this.state=state;this.parent=parent;}
    @Override protected void init(){
        VideoPackets.SessionState current=state.session(controllerPos); TvDisplaySettings settings=current==null?TvDisplaySettings.defaults(PresentationMode.FIT):current.displaySettings(); draft=new DisplaySettingsDraft(settings);
        int left=Math.max(4,(width-312)/2),y=28;
        for(PresentationMode mode:PresentationMode.values())addRenderableWidget(Button.builder(Component.literal(mode.name()),b->draft.layout(mode)).bounds(left+mode.ordinal()*72,y,68,20).build());
        for(PixelMapping mapping:PixelMapping.values()){Button b=Button.builder(Component.literal(mapping==PixelMapping.DETAILED?"Detailed":"One/block"),v->draft.mapping(mapping)).bounds(left+mapping.ordinal()*100,y+26,96,20).build();b.active=settings.origin()!=TvDisplaySettings.Origin.QUICK;addRenderableWidget(b);}
        Button auto=Button.builder(Component.literal("Auto"),b->draft.resolution(ResolutionChoice.AUTO)).bounds(left,y+52,70,20).build();auto.active=settings.origin()!=TvDisplaySettings.Origin.QUICK;addRenderableWidget(auto);
        Button preset=Button.builder(Component.literal("1080p"),b->draft.resolution(ResolutionChoice.preset("1080p"))).bounds(left+74,y+52,70,20).build();preset.active=settings.origin()!=TvDisplaySettings.Origin.QUICK;addRenderableWidget(preset);
        widthBox=new EditBox(font,left+148,y+52,72,20,Component.literal("Width"));heightBox=new EditBox(font,left+224,y+52,72,20,Component.literal("Height"));widthBox.setMaxLength(4);heightBox.setMaxLength(4);addRenderableWidget(widthBox);addRenderableWidget(heightBox);
        Button custom=Button.builder(Component.literal("Custom"),b->applyCustom()).bounds(left+148,y+76,72,20).build();custom.active=settings.origin()!=TvDisplaySettings.Origin.QUICK;addRenderableWidget(custom);
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->apply()).bounds(left+140,205,72,20).build()); addRenderableWidget(Button.builder(Component.literal("Cancel"),b->minecraft.setScreen(parent)).bounds(left+216,205,72,20).build());
    }
    private void applyCustom(){try{draft.resolution(ResolutionChoice.custom(Integer.parseInt(widthBox.getValue().trim()),Integer.parseInt(heightBox.getValue().trim())));error=draft.error();}catch(RuntimeException e){error=e.getMessage()==null?"Invalid resolution":e.getMessage();}}
    private void apply(){try{TvDisplaySettings next=draft.apply();VideoPackets.SessionState c=state.session(controllerPos);if(c==null){error="TV state is unavailable";return;}state.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_DISPLAY,controllerPos,"",c.item()==null?"":c.item().key(),"",c.presentationMode(),c.timelineGeneration(),CinemarrVideoPlayback.authoritativePositionMsLocal(c),c.selectedAudioStreamId(),c.selectedSubtitleStreamId()).withDisplay(next));minecraft.setScreen(parent);}catch(RuntimeException e){error=e.getMessage()==null?"Unable to apply settings":e.getMessage();}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g,mx,my,partial);super.render(g,mx,my,partial);g.drawCenteredString(font,title,width/2,8,0xffffffff);VideoPackets.SessionState v=state.session(controllerPos);String e=v==null?"Actual: unavailable":"Actual: "+v.effectiveWidth()+"x"+v.effectiveHeight()+"  Screen: "+v.screenWidth()+"x"+v.screenHeight();g.drawCenteredString(font,e,width/2,156,0xffa0d8ff);if(!error.isEmpty())g.drawCenteredString(font,error,width/2,180,0xffff8080);}
}
