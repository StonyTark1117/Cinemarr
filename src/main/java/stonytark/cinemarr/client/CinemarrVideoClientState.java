package stonytark.cinemarr.client;

import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

/** Client-side generation gate and pull-window coordinator ahead of the native decoder. */
public final class CinemarrVideoClientState {
    public static final CinemarrVideoClientState INSTANCE=new CinemarrVideoClientState();
    private final VideoSegmentAssembler assembler=new VideoSegmentAssembler();
    private final Queue<VideoSegmentAssembler.CompletedSegment> ready=new ArrayDeque<>();
    private VideoPackets.LibraryList libraries=new VideoPackets.LibraryList(Collections.emptyList());
    private VideoPackets.BrowseResults browse=new VideoPackets.BrowseResults("","","",0,false,Collections.emptyList());
    private VideoPackets.SessionState session;
    private VideoPackets.SegmentManifest manifest;
    private long requestId;
    private int requestedSegment=-1;
    private int currentWindowEnd;
    private int totalChunks;

    public boolean accept(CinemarrMessage payload){
        if(payload instanceof VideoPayloads.LibraryList value){libraries=value.value();return true;}
        if(payload instanceof VideoPayloads.BrowseResults value){browse=value.value();return true;}
        if(payload instanceof VideoPayloads.SessionState value){
            VideoPackets.SessionState next=value.value();
            if(session==null||!session.sessionId().equals(next.sessionId())||session.generation()!=next.generation())resetMedia();
            session=next;return true;
        }
        if(payload instanceof VideoPayloads.SegmentManifest value){
            VideoPackets.SegmentManifest next=value.value();
            if(session==null||!session.sessionId().equals(next.sessionId())||session.generation()!=next.generation())return true;
            manifest=next;ready.clear();int first=seekSegment(next,session.positionMs());request(first,0);return true;
        }
        if(payload instanceof VideoPayloads.SegmentChunk value){chunk(value.value());return true;}
        return false;
    }

    private void chunk(VideoPackets.SegmentChunk value){
        if(session==null||manifest==null||!session.sessionId().equals(value.sessionId())||session.generation()!=value.generation()
                ||value.segmentIndex()!=requestedSegment||value.totalChunks()<1)return;
        if(totalChunks==0){
            totalChunks=value.totalChunks();assembler.begin(value.sessionId(),value.generation(),value.requestId(),value.segmentIndex(),
                    value.totalChunks(),value.segmentSha256(),value.presentationTimeMs(),value.keyframe());
        }
        Optional<VideoSegmentAssembler.CompletedSegment> completed=assembler.accept(value.sessionId(),value.generation(),value.requestId(),
                value.segmentIndex(),value.chunkIndex(),value.totalChunks(),value.segmentSha256(),value.presentationTimeMs(),value.keyframe(),value.data());
        if(completed.isPresent()){
            ready.add(completed.get());
            CinemarrNetwork.sendToServer(new VideoPayloads.SegmentAcknowledgement(new VideoPackets.SegmentAcknowledgement(
                    value.sessionId(),value.generation(),value.requestId(),value.segmentIndex(),value.totalChunks()-1,bufferedMs())));
            if(value.segmentIndex()+1<manifest.segments().size())request(value.segmentIndex()+1,0);
        }else if(value.chunkIndex()+1>=currentWindowEnd&&currentWindowEnd<totalChunks){request(value.segmentIndex(),currentWindowEnd);}
    }

    private void request(int segment,int firstChunk){
        if(manifest==null||segment<0||segment>=manifest.segments().size())return;
        requestedSegment=segment;totalChunks=firstChunk==0?0:totalChunks;currentWindowEnd=firstChunk+8;
        CinemarrNetwork.sendToServer(new VideoPayloads.SegmentRequest(new VideoPackets.SegmentRequest(
                manifest.sessionId(),manifest.generation(),++requestId,segment,firstChunk,8)));
    }

    private static int seekSegment(VideoPackets.SegmentManifest manifest,long positionMs){
        int result=0;for(int index=0;index<manifest.segments().size();index++){
            VideoPackets.SegmentDescriptor value=manifest.segments().get(index);
            if(value.presentationTimeMs()>positionMs)break;if(value.keyframe())result=index;
        }return result;
    }
    private long bufferedMs(){long total=0;for(VideoSegmentAssembler.CompletedSegment segment:ready){if(manifest!=null&&segment.segmentIndex()<manifest.segments().size())total+=manifest.segments().get(segment.segmentIndex()).durationMs();}return total;}
    private void resetMedia(){assembler.reset();ready.clear();manifest=null;requestedSegment=-1;currentWindowEnd=0;totalChunks=0;}
    public void reset(){libraries=new VideoPackets.LibraryList(Collections.emptyList());browse=new VideoPackets.BrowseResults("","","",0,false,Collections.emptyList());session=null;resetMedia();}
    public void requestLibraries(){CinemarrNetwork.sendToServer(new VideoPayloads.LibraryListRequest());}
    public void browse(String libraryId,String parentKey,String query,int page){CinemarrNetwork.sendToServer(new VideoPayloads.BrowseRequest(new VideoPackets.BrowseRequest(libraryId,parentKey,query,page)));}
    public void command(VideoPackets.SessionCommand command){CinemarrNetwork.sendToServer(new VideoPayloads.SessionCommand(command));}
    public VideoPackets.LibraryList libraries(){return libraries;} public VideoPackets.BrowseResults browse(){return browse;}
    public VideoPackets.SessionState session(){return session;} public VideoPackets.SegmentManifest manifest(){return manifest;}
    public VideoSegmentAssembler.CompletedSegment pollSegment(){return ready.poll();} public int readySegments(){return ready.size();}
    private CinemarrVideoClientState(){}
}
