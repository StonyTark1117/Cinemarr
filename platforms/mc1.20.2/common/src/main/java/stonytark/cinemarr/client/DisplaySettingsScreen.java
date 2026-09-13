package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.DisplaySettingsPage;

/** Compact adapter; the draft and acknowledgement state survive widget rebuilds. */
public final class DisplaySettingsScreen extends Screen implements DisplaySettingsErrorTarget {
    private final long controllerPos;
    private final CinemarrVideoClientState state;
    private final CinemarrVideoScreen parent;
    private DisplaySettingsPage page;
    private EditBox widthBox, heightBox;
    private Button layout, mapping, quality, apply, reload;

    public DisplaySettingsScreen(long pos, CinemarrVideoClientState state, CinemarrVideoScreen parent) {
        super(Component.literal("Display Settings")); controllerPos=pos; this.state=state; this.parent=parent;
    }
    @Override protected void init() {
        if(page==null)page=new DisplaySettingsPage(state.session(controllerPos));
        if (stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display UI: width={} height={} controller={} editable={}", width, height, controllerPos, page.editable());
        int left=Math.max(4,(width-304)/2);
        layout=button(left,28,304,page.layoutLabel(),()->page.cycleLayout());
        mapping=button(left,52,304,page.mappingLabel(),()->page.cycleMapping());
        quality=button(left,76,304,page.resolutionLabel(),()->page.cycleResolution());
        widthBox=new EditBox(font,left+40,102,104,20,Component.literal("Width"));
        heightBox=new EditBox(font,left+196,102,104,20,Component.literal("Height"));
        widthBox.setMaxLength(4);heightBox.setMaxLength(4);
        widthBox.setValue(page.width());heightBox.setValue(page.height());
        widthBox.setResponder(value->page.dimensions(value,heightBox.getValue()));
        heightBox.setResponder(value->page.dimensions(widthBox.getValue(),value));
        addRenderableWidget(widthBox);addRenderableWidget(heightBox);
        reload=button(left,208,96,"Reload",()->{page.reload(state.session(controllerPos));String w=page.width(),h=page.height();widthBox.setValue(w);heightBox.setValue(h);});
        apply=button(left+104,208,96,"Apply",()->{VideoPackets.SessionCommand command=page.apply(controllerPos,System.currentTimeMillis());if(command!=null)state.command(command);});
        button(left+208,208,96,"Cancel",()->onClose());
        refresh();
    }
    private Button button(int x,int y,int w,String label,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),b->{action.run();refresh();}).bounds(x,y,w,20).build());
    }
    private void refresh() {
        layout.setMessage(Component.literal(page.layoutLabel()));mapping.setMessage(Component.literal(page.mappingLabel()));quality.setMessage(Component.literal(page.resolutionLabel()));
        layout.active=page.editable();mapping.active=quality.active=page.qualityEditable();apply.active=page.editable();reload.active=!page.pending();
        widthBox.setEditable(page.customEditable());heightBox.setEditable(page.customEditable());widthBox.active=heightBox.active=page.customEditable();
    }
    @Override public void tick(){page.update(state.session(controllerPos),System.currentTimeMillis());refresh();}
    public void showError(String message){page.fail(message);refresh();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    private void line(GuiGraphics g,String text,int y,int color){g.drawCenteredString(font,font.plainSubstrByWidth(text,304),width/2,y,color);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g,mx,my,partial);super.render(g,mx,my,partial);
        line(g,"Display Settings",8,0xffffffff);
        int left=Math.max(4,(width-304)/2);g.drawString(font,"Width",left,108,0xffffffff);g.drawString(font,"Height",left+152,108,0xffffffff);
        line(g,page.requestedLabel(),130,0xffa0d8ff);
        line(g,"Actual decoded: "+state.actualDimensions(controllerPos),142,0xffa0d8ff);
        VideoPackets.SessionState current=state.session(controllerPos);
        line(g,current==null?"Screen: unknown":"Screen: "+current.screenWidth()+"x"+current.screenHeight()+" blocks",154,0xffa0d8ff);
        line(g,"Pixel mode outputs one pixel per screen block.",166,0xffffffff);
        line(g,"Quality is limited by source and server caps.",178,0xffffffff);
        line(g,page.message(),192,0xffffb36b);
    }
}
