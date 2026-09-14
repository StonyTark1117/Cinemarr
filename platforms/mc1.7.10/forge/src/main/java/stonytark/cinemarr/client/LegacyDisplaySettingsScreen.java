package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.DisplaySettingsPage;

/** Java 8 GUI adapter for the shared display draft and acknowledgement model. */
final class LegacyDisplaySettingsScreen extends GuiScreen {
    private final long pos;
    private final LegacyVideoClientState state;
    private final GuiScreen parent;
    private DisplaySettingsPage page;
    private GuiTextField widthBox, heightBox;
    LegacyDisplaySettingsScreen(long pos, LegacyVideoClientState state, GuiScreen parent) {
        this.pos=pos;this.state=state;this.parent=parent;
    }
    @Override public void initGui() {
        if(page==null)page=new DisplaySettingsPage(state.session(pos));
        Keyboard.enableRepeatEvents(true);buttonList.clear();
        if (stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display UI: width={} height={} controller={} editable={}", width, height, pos, page.editable());
        int left=Math.max(4,(width-304)/2);
        add(0,left,28,304,page.layoutLabel());add(1,left,52,304,page.mappingLabel());add(2,left,76,304,page.resolutionLabel());
        widthBox=new LegacyDisplayTextField(fontRendererObj,left+40,102,104,20);heightBox=new LegacyDisplayTextField(fontRendererObj,left+196,102,104,20);
        widthBox.setMaxStringLength(4);heightBox.setMaxStringLength(4);widthBox.setText(page.width());heightBox.setText(page.height());
        add(3,left,208,96,"Reload");add(4,left+104,208,96,"Apply");add(5,left+208,208,96,"Cancel");refresh();
    }
    private void add(int id,int x,int y,int w,String text){buttonList.add(new LegacyVideoButton(id,x,y,w,20,text));}
    private void refresh(){
        for(Object object:buttonList){GuiButton b=(GuiButton)object;
            if(b.id==0){b.displayString=page.layoutLabel();b.enabled=page.editable();}
            if(b.id==1){b.displayString=page.mappingLabel();b.enabled=page.qualityEditable();}
            if(b.id==2){b.displayString=page.resolutionLabel();b.enabled=page.qualityEditable();}
            if(b.id==3)b.enabled=!page.pending();if(b.id==4)b.enabled=page.editable();
        }
        widthBox.setEnabled(page.customEditable());heightBox.setEnabled(page.customEditable());
        if(!page.customEditable()){widthBox.setFocused(false);heightBox.setFocused(false);}
    }
    @Override protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        switch(b.id){
            case 0:page.cycleLayout();break;case 1:page.cycleMapping();break;case 2:page.cycleResolution();break;
            case 3:page.reload(state.session(pos));widthBox.setText(page.width());heightBox.setText(page.height());break;
            case 4:VideoPackets.SessionCommand command=page.apply(pos,System.currentTimeMillis());if(command!=null)state.command(command);break;
            case 5:mc.displayGuiScreen(parent);break;default:break;
        }
        refresh();
    }
    @Override public void updateScreen(){page.update(state.session(pos),System.currentTimeMillis());widthBox.updateCursorCounter();heightBox.updateCursorCounter();refresh();}
    @Override protected void keyTyped(char character,int key){
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        if(page.customEditable()){widthBox.textboxKeyTyped(character,key);heightBox.textboxKeyTyped(character,key);page.dimensions(widthBox.getText(),heightBox.getText());}
    }
    @Override protected void mouseClicked(int x,int y,int button){super.mouseClicked(x,y,button);if(page.customEditable()){widthBox.mouseClicked(x,y,button);heightBox.mouseClicked(x,y,button);}}
    void showError(String error){page.fail(error);refresh();}
    private void line(String text,int y,int color){drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(text,304),width/2,y,color);}
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();super.drawScreen(x,y,partial);widthBox.drawTextBox();heightBox.drawTextBox();
        line("Display Settings",8,0xffffffff);int left=Math.max(4,(width-304)/2);
        drawString(fontRendererObj,"Width",left,108,0xffffffff);drawString(fontRendererObj,"Height",left+152,108,0xffffffff);
        line(page.requestedLabel(),130,0xffa0d8ff);line("Actual decoded: "+state.actualDimensions(pos),142,0xffa0d8ff);
        VideoPackets.SessionState current=state.session(pos);line(current==null?"Screen: unknown":"Screen: "+current.screenWidth()+"x"+current.screenHeight()+" blocks",154,0xffa0d8ff);
        line("Pixel mode outputs one pixel per screen block.",166,0xffffffff);line("Quality is limited by source and server caps.",178,0xffffffff);line(page.message(),192,0xffffb36b);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
}
