package stonytark.cinemarr.core.client;

/** Logical-pixel controller geometry shared by legacy and modern clients.
 * Minecraft's normal scaled viewport is at least 320 by 240. */
public final class VideoControllerLayout {
    public enum Slot {
        SEARCH, GO, BACK, REFRESH, QUEUE, LIBRARY_PREVIOUS, LIBRARY_NEXT,
        AUDIO, SUBTITLES, CONTINUE, PAUSE, SEEK_BACK, SEEK_FORWARD, STOP, SKIP,
        FIT, FILL, STRETCH, SCREEN, SESSION, TUNE, VOLUME_DOWN, VOLUME, VOLUME_UP
    }
    public static final class Box {
        public final int x, y, width, height;
        public Box(int x, int y, int width, int height) {
            this.x=x; this.y=y; this.width=width; this.height=height;
        }
        public boolean overlaps(Box other) {
            return x < other.x+other.width && other.x < x+width
                    && y < other.y+other.height && other.y < y+height;
        }
        public boolean fits(int viewportWidth, int viewportHeight) {
            return width>0 && height>0 && x>=0 && y>=0
                    && x+width<=viewportWidth && y+height<=viewportHeight;
        }
    }
    private final int panel, left, bottom, streams, playback, presentation;
    public VideoControllerLayout(int width, int height) {
        panel=Math.min(760, width-16); left=(width-panel)/2; bottom=height-26;
        boolean compact=panel<576;
        streams=height-(compact?98:74);
        playback=streams+24;
        presentation=compact?playback+24:playback;
    }
    public int left(){return left;}
    public int panel(){return panel;}
    public int contentTop(){return 80;}
    public int noticeY(){return streams-14;}
    public int rows(boolean queue){return Math.max(1,(noticeY()-4-contentTop()-(queue?0:22))/22);}
    public int pagerY(){return contentTop()+rows(false)*22;}
    public int libraryCapacity(){return Math.max(1,(panel-56)/100);}
    public int libraryPages(int count){return Math.max(1,(count+libraryCapacity()-1)/libraryCapacity());}
    public Box library(int visibleIndex){
        int size=(panel-56)/libraryCapacity();
        return box(28+visibleIndex*size,32,size-4);
    }
    private Box box(int x,int y,int width){return new Box(left+x,y,width,20);}
    public Box slot(Slot slot){
        int session=Math.min(180,panel-212), stream=Math.min(180,(panel-78)/2);
        int mode=playback==presentation?296:0;
        switch(slot){
            case SEARCH:return box(0,56,panel-212);
            case GO:return box(panel-208,56,40);
            case BACK:return box(panel-164,56,46);
            case REFRESH:return box(panel-114,56,54);
            case QUEUE:return box(panel-56,56,56);
            case LIBRARY_PREVIOUS:return box(0,32,24);
            case LIBRARY_NEXT:return box(panel-24,32,24);
            case AUDIO:return box(0,streams,stream);
            case SUBTITLES:return box(stream+4,streams,stream);
            case CONTINUE:return box(2*stream+8,streams,70);
            case PAUSE:return box(0,playback,70);
            case SEEK_BACK:return box(74,playback,52);
            case SEEK_FORWARD:return box(130,playback,52);
            case STOP:return box(186,playback,54);
            case SKIP:return box(244,playback,48);
            case FIT:return box(mode,presentation,58);
            case FILL:return box(mode+62,presentation,58);
            case STRETCH:return box(mode+124,presentation,58);
            case SCREEN:return box(panel-86,presentation,86);
            case SESSION:return box(0,bottom,session);
            case TUNE:return box(session+4,bottom,48);
            case VOLUME_DOWN:return box(session+60,bottom,48);
            case VOLUME:return box(session+112,bottom,48);
            case VOLUME_UP:return box(session+164,bottom,48);
            default:throw new AssertionError(slot);
        }
    }
}
