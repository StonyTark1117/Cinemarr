package stonytark.cinemarr.core.client;

import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.*;

/** Opt-in acceptance commands use the same draft and wire command as the display page. */
public final class DisplayFeatureProbe {
    private DisplayFeatureProbe() {}
    public static VideoPackets.SessionCommand command(String operation, VideoPackets.SessionState state) {
        String[] fields=operation.split(":",-1);
        if(fields.length<3||!fields[0].equals("video")||Long.parseLong(fields[2])!=state.controllerPos())return null;
        if(fields[1].equals("display-tune")&&fields.length==4)
            return new VideoPackets.SessionCommand(VideoPackets.SessionAction.TUNE,state.controllerPos(),"","",fields[3],state.presentationMode(),state.timelineGeneration(),0,-1,-1);
        if(fields[1].equals("display-transport")&&fields.length==4
                && (fields[3].equals("PAUSE")||fields[3].equals("RESUME")))
            return new VideoPackets.SessionCommand(VideoPackets.SessionAction.valueOf(fields[3]),state.controllerPos(),"","","",
                    state.presentationMode(),state.timelineGeneration(),state.positionMs(),state.selectedAudioStreamId(),state.selectedSubtitleStreamId());
        if(!fields[1].equals("display")||fields.length!=6)return null;
        ResolutionChoice resolution;
        if(fields[5].equals("auto"))resolution=ResolutionChoice.AUTO;
        else if(fields[5].contains("x")){String[] dimensions=fields[5].split("x");resolution=ResolutionChoice.custom(Integer.parseInt(dimensions[0]),Integer.parseInt(dimensions[1]));}
        else resolution=ResolutionChoice.preset(fields[5]);
        DisplaySettingsDraft draft=new DisplaySettingsDraft(state.displaySettings()).layout(PresentationMode.valueOf(fields[3]))
                .mapping(PixelMapping.valueOf(fields[4])).resolution(resolution);
        return new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_DISPLAY,state.controllerPos(),"","","",draft.layout(),state.timelineGeneration(),0,-1,-1).withDisplay(draft.apply());
    }
    public static String describe(VideoPackets.SessionState s,String actual) {
        TvDisplaySettings d=s.displaySettings();
        return "controller="+s.controllerPos()+" tv="+s.televisionId()+" timeline="+s.timelineId()+" timelineGeneration="+s.timelineGeneration()
                +" stream="+s.sessionId()+" streamGeneration="+s.generation()+" revision="+d.revision()+" origin="+d.origin()
                +" layout="+d.layout()+" mapping="+d.mapping()+" requested="+d.resolution()+" actual="+actual
                +" width="+s.screenWidth()+" height="+s.screenHeight()+" status="+s.status()+" position="+s.positionMs()+" canControl="+s.canControl()
                +" waiting="+s.message().equals("Waiting for stream capacity")
                +" failed="+s.message().startsWith("Unable to prepare TV stream;");
    }
}
