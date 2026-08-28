package stonytark.cinemarr.client;

import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import stonytark.cinemarr.core.library.QueuedVideo;

/** All visible televisions plus one compressed/decode stream per distinct watch-party generation. */
public final class CinemarrVideoClientState {
    public static final CinemarrVideoClientState INSTANCE=new CinemarrVideoClientState();
    private final Map<Long,VideoPackets.SessionState> televisions=new LinkedHashMap<>();
    private final Map<StreamKey,StreamState> streams=new LinkedHashMap<>();
    private final Map<UUID,List<QueuedVideo>> queues=new LinkedHashMap<>();
    private VideoPackets.LibraryList libraries=new VideoPackets.LibraryList(Collections.emptyList());
    private VideoPackets.BrowseResults browse=new VideoPackets.BrowseResults("","","",0,false,Collections.emptyList());

    public boolean accept(CinemarrMessage payload){
        if(payload instanceof VideoPayloads.LibraryList value){libraries=value.value();return true;}
        if(payload instanceof VideoPayloads.BrowseResults value){browse=value.value();return true;}
        if(payload instanceof VideoPayloads.SessionState value){acceptSession(value.value());return true;}
        if(payload instanceof VideoPayloads.TelevisionRemoved value){removeTelevision(value.value().controllerPos());return true;}
        if(payload instanceof VideoPayloads.SessionQueue value){queues.put(value.value().sessionId(),value.value().entries());return true;}
        if(payload instanceof VideoPayloads.SegmentManifest value){
            VideoPackets.SegmentManifest next=value.value();StreamState stream=streams.get(new StreamKey(next.sessionId(),next.generation()));
            if(stream!=null)stream.manifest(next);return true;
        }
        if(payload instanceof VideoPayloads.SegmentChunk value){
            StreamState stream=streams.get(new StreamKey(value.value().sessionId(),value.value().generation()));if(stream!=null)stream.chunk(value.value());return true;
        }
        return false;
    }

    private void acceptSession(VideoPackets.SessionState next){
        televisions.put(next.controllerPos(),next);
        if(next.item()!=null&&!next.sessionId().equals(new UUID(0,0))){
            StreamKey key=new StreamKey(next.sessionId(),next.generation());
            streams.computeIfAbsent(key,ignored->new StreamState(key)).session(next);
        }
        pruneStreams();
    }

    private void removeTelevision(long controllerPos){televisions.remove(controllerPos);pruneStreams();}
    private void pruneStreams(){
        List<StreamKey> referenced=new ArrayList<>();
        for(VideoPackets.SessionState state:televisions.values())if(state.item()!=null&&!state.sessionId().equals(new UUID(0,0)))referenced.add(new StreamKey(state.sessionId(),state.generation()));
        streams.entrySet().removeIf(entry->{if(referenced.contains(entry.getKey()))return false;entry.getValue().reset();return true;});
        java.util.Set<UUID> visibleSessions=new java.util.HashSet<>();for(VideoPackets.SessionState state:televisions.values())if(!state.sessionId().equals(new UUID(0,0)))visibleSessions.add(state.sessionId());queues.keySet().retainAll(visibleSessions);
    }

    public void reset(){libraries=new VideoPackets.LibraryList(Collections.emptyList());browse=new VideoPackets.BrowseResults("","","",0,false,Collections.emptyList());televisions.clear();queues.clear();for(StreamState value:streams.values())value.reset();streams.clear();}
    public void requestLibraries(){CinemarrNetwork.sendToServer(new VideoPayloads.LibraryListRequest());}
    public void browse(String libraryId,String parentKey,String query,int page){CinemarrNetwork.sendToServer(new VideoPayloads.BrowseRequest(new VideoPackets.BrowseRequest(libraryId,parentKey,query,page)));}
    public void command(VideoPackets.SessionCommand command){CinemarrNetwork.sendToServer(new VideoPayloads.SessionCommand(command));}
    public VideoPackets.LibraryList libraries(){return libraries;} public VideoPackets.BrowseResults browse(){return browse;}
    public VideoPackets.SessionState session(long controllerPos){return televisions.get(controllerPos);}
    public Collection<VideoPackets.SessionState> televisions(){return List.copyOf(televisions.values());}
    public List<QueuedVideo> queue(long controllerPos){VideoPackets.SessionState value=televisions.get(controllerPos);return value==null?Collections.emptyList():queues.getOrDefault(value.sessionId(),Collections.emptyList());}
    Collection<StreamState> streamStates(){return List.copyOf(streams.values());}
    StreamState stream(StreamKey key){return streams.get(key);}
    List<VideoPackets.SessionState> televisionsForStream(StreamKey key){List<VideoPackets.SessionState> values=new ArrayList<>();for(VideoPackets.SessionState state:televisions.values())if(state.sessionId().equals(key.sessionId())&&state.generation()==key.generation())values.add(state);return values;}

    record StreamKey(UUID sessionId,long generation) {}

    static final class StreamState {
        private static final int MAX_READY_SEGMENTS = 4;
        private final StreamKey key;
        private final VideoSegmentAssembler assembler=new VideoSegmentAssembler();
        private final Queue<VideoSegmentAssembler.CompletedSegment> ready=new ArrayDeque<>();
        private VideoPackets.SessionState session;
        private VideoPackets.SegmentManifest manifest;
        private long requestId;
        private int requestedSegment=-1,currentWindowEnd,totalChunks;
        private int deferredSegment=-1;

        StreamState(StreamKey key){this.key=key;}
        void session(VideoPackets.SessionState value){session=value;}
        VideoPackets.SessionState session(){return session;}
        StreamKey key(){return key;}

        void manifest(VideoPackets.SegmentManifest next){
            if(!key.sessionId.equals(next.sessionId())||key.generation!=next.generation()||session==null)return;
            if(sameWindow(manifest,next)&&requestedSegment>=0){manifest=next;return;}
            boolean continuation=manifest!=null&&!next.segments().isEmpty()&&next.segments().get(0).index()==requestedSegment+1;
            manifest=next;if(continuation){int first=next.segments().get(0).index();if(ready.size()<MAX_READY_SEGMENTS)request(first,0);else deferredSegment=first;}else{ready.clear();deferredSegment=-1;int first=seekSegment(next,session.positionMs());request(first,0);}
        }
        void chunk(VideoPackets.SegmentChunk value){
            if(manifest==null||value.requestId()!=requestId||value.segmentIndex()!=requestedSegment||value.totalChunks()<1)return;
            if(totalChunks==0){totalChunks=value.totalChunks();assembler.begin(value.sessionId(),value.generation(),value.requestId(),value.segmentIndex(),value.totalChunks(),value.segmentSha256(),value.presentationTimeMs(),value.keyframe());}
            Optional<VideoSegmentAssembler.CompletedSegment> completed=assembler.accept(value.sessionId(),value.generation(),value.requestId(),value.segmentIndex(),value.chunkIndex(),value.totalChunks(),value.segmentSha256(),value.presentationTimeMs(),value.keyframe(),value.data());
            if(completed.isPresent()){
                ready.add(completed.get());CinemarrNetwork.sendToServer(new VideoPayloads.SegmentAcknowledgement(new VideoPackets.SegmentAcknowledgement(value.sessionId(),value.generation(),value.requestId(),value.segmentIndex(),value.totalChunks()-1,bufferedMs())));
                int local=descriptorIndex(value.segmentIndex());if(local>=0&&local+1<manifest.segments().size()){int next=manifest.segments().get(local+1).index();if(ready.size()<MAX_READY_SEGMENTS)request(next,0);else deferredSegment=next;}else if(manifest.hasMore())CinemarrNetwork.sendToServer(new VideoPayloads.SegmentManifestRequest(new VideoPackets.SegmentManifestRequest(key.sessionId,key.generation,value.segmentIndex()+1)));
            }else if(value.chunkIndex()+1>=currentWindowEnd&&currentWindowEnd<totalChunks)request(value.segmentIndex(),currentWindowEnd);
        }
        private void request(int segment,int firstChunk){
            int descriptor=descriptorIndex(segment);if(manifest==null||descriptor<0)return;
            if(firstChunk==0&&!withinPrefetchLead(manifest.segments().get(descriptor).presentationTimeMs(),CinemarrVideoPlayback.authoritativePositionMsLocal(session))){deferredSegment=segment;return;}
            deferredSegment=-1;requestedSegment=segment;if(firstChunk==0){totalChunks=0;requestId++;}currentWindowEnd=firstChunk+8;
            CinemarrNetwork.sendToServer(new VideoPayloads.SegmentRequest(new VideoPackets.SegmentRequest(key.sessionId,key.generation,requestId,segment,firstChunk,8)));
        }
        static boolean withinPrefetchLead(long segmentPresentationTimeMs,long playbackPositionMs){return segmentPresentationTimeMs<=playbackPositionMs+ProtocolLimits.CLIENT_VIDEO_PREFETCH_LEAD_MS;}
        private int descriptorIndex(int segment){if(manifest==null)return -1;for(int index=0;index<manifest.segments().size();index++)if(manifest.segments().get(index).index()==segment)return index;return -1;}
        private static boolean sameWindow(VideoPackets.SegmentManifest left,VideoPackets.SegmentManifest right){if(left==null||right==null||left.segments().size()!=right.segments().size())return false;if(left.segments().isEmpty())return true;return left.segments().get(0).index()==right.segments().get(0).index()&&left.segments().get(left.segments().size()-1).index()==right.segments().get(right.segments().size()-1).index();}
        private static int seekSegment(VideoPackets.SegmentManifest manifest,long positionMs){int result=manifest.segments().isEmpty()?-1:manifest.segments().get(0).index();for(VideoPackets.SegmentDescriptor value:manifest.segments()){if(value.presentationTimeMs()>positionMs)break;if(value.keyframe())result=value.index();}return result;}
        private long bufferedMs(){long total=0;for(VideoSegmentAssembler.CompletedSegment segment:ready){int local=descriptorIndex(segment.segmentIndex());if(local>=0)total+=manifest.segments().get(local).durationMs();}return total;}
        void reset(){assembler.reset();ready.clear();manifest=null;requestedSegment=-1;currentWindowEnd=0;totalChunks=0;deferredSegment=-1;}
        VideoSegmentAssembler.CompletedSegment pollSegment(){if(deferredSegment>=0&&ready.size()<MAX_READY_SEGMENTS)request(deferredSegment,0);VideoSegmentAssembler.CompletedSegment value=ready.poll();if(deferredSegment>=0&&ready.size()<MAX_READY_SEGMENTS)request(deferredSegment,0);return value;}
        VideoPackets.SegmentManifest manifest(){return manifest;}
    }
    private CinemarrVideoClientState(){}
}
