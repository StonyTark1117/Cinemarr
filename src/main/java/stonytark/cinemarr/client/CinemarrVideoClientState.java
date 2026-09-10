package stonytark.cinemarr.client;

import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.client.TransferWindowFlow;
import stonytark.cinemarr.core.client.SegmentPrefetchPolicy;
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
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import stonytark.cinemarr.core.library.QueuedVideo;

/** All visible televisions plus one compressed/decode stream per distinct TV stream generation. */
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
            VideoPackets.SegmentManifest next=value.value();StreamState stream=streams.get(new StreamKey(next.identity()));
            if(stream!=null)stream.manifest(next);return true;
        }
        if(payload instanceof VideoPayloads.SegmentChunk value){
            StreamState stream=streams.get(new StreamKey(value.value().identity()));if(stream!=null)stream.chunk(value.value());return true;
        }
        return false;
    }

    private void acceptSession(VideoPackets.SessionState next){
        televisions.put(next.controllerPos(),next);
        if(next.item()!=null&&!next.sessionId().equals(new UUID(0,0))){
            StreamKey key=new StreamKey(next.identity());
            streams.computeIfAbsent(key,ignored->new StreamState(key)).session(next);
        }
        pruneStreams();
    }

    private void removeTelevision(long controllerPos){televisions.remove(controllerPos);pruneStreams();}
    private void pruneStreams(){
        List<StreamKey> referenced=new ArrayList<>();
        for(VideoPackets.SessionState state:televisions.values())if(state.item()!=null&&!state.sessionId().equals(new UUID(0,0)))referenced.add(new StreamKey(state.identity()));
        streams.entrySet().removeIf(entry->{if(referenced.contains(entry.getKey()))return false;entry.getValue().reset();return true;});
        java.util.Set<UUID> visibleSessions=new java.util.HashSet<>();for(VideoPackets.SessionState state:televisions.values())if(!state.timelineId().equals(new UUID(0,0)))visibleSessions.add(state.timelineId());queues.keySet().retainAll(visibleSessions);
    }

    public void reset(){libraries=new VideoPackets.LibraryList(Collections.emptyList());browse=new VideoPackets.BrowseResults("","","",0,false,Collections.emptyList());televisions.clear();queues.clear();for(StreamState value:streams.values())value.reset();streams.clear();}
    public void requestLibraries(){CinemarrNetwork.sendToServer(new VideoPayloads.LibraryListRequest());}
    public void browse(String libraryId,String parentKey,String query,int page){CinemarrNetwork.sendToServer(new VideoPayloads.BrowseRequest(new VideoPackets.BrowseRequest(libraryId,parentKey,query,page)));}
    public void command(VideoPackets.SessionCommand command){CinemarrNetwork.sendToServer(new VideoPayloads.SessionCommand(command));}
    public VideoPackets.LibraryList libraries(){return libraries;} public VideoPackets.BrowseResults browse(){return browse;}
    public VideoPackets.SessionState session(long controllerPos){return televisions.get(controllerPos);}
    public Collection<VideoPackets.SessionState> televisions(){return List.copyOf(televisions.values());}
    public List<QueuedVideo> queue(long controllerPos){VideoPackets.SessionState value=televisions.get(controllerPos);return value==null?Collections.emptyList():queues.getOrDefault(value.timelineId(),Collections.emptyList());}
    Collection<StreamState> streamStates(){return List.copyOf(streams.values());}
    StreamState stream(StreamKey key){return streams.get(key);}
    List<VideoPackets.SessionState> televisionsForStream(StreamKey key){List<VideoPackets.SessionState> values=new ArrayList<>();for(VideoPackets.SessionState state:televisions.values())if(state.identity().equals(key.identity()))values.add(state);return values;}

    record StreamKey(VideoStreamIdentity identity) {
        StreamKey(UUID sessionId, long generation) { this(new VideoStreamIdentity(sessionId, generation, sessionId, generation)); }
        UUID sessionId() { return identity.streamId(); }
        long generation() { return identity.streamGeneration(); }
    }

    static final class StreamState {
        private final StreamKey key;
        private final VideoSegmentAssembler assembler=new VideoSegmentAssembler();
        private final Queue<VideoSegmentAssembler.CompletedSegment> ready=new ArrayDeque<>();
        private VideoPackets.SessionState session;
        private VideoPackets.SegmentManifest manifest;
        private long requestId;
        private int requestedSegment=-1,lastCompletedSegment=-1,currentWindowStart,totalChunks,requestRetries;
        private long requestSentAt;
        private long readyBytes;
        private int deferredSegment=-1;
        private boolean finalSegmentReceived;

        StreamState(StreamKey key){this.key=key;}
        void session(VideoPackets.SessionState value){session=value;}
        VideoPackets.SessionState session(){return session;}
        StreamKey key(){return key;}

        void manifest(VideoPackets.SegmentManifest next){
            if(!key.identity().equals(next.identity())||session==null)return;
            // A refresh must not replace the page owning an active transfer,
            // deferred prefetch, or completed end-of-stream state.
            if(manifest!=null&&(requestedSegment>=0||deferredSegment>=0||finalSegmentReceived))return;
            int first=resumeSegment(next,System.currentTimeMillis());
            if(first<0||next.segments().isEmpty()||first>next.segments().get(next.segments().size()-1).index())return;
            manifest=next;if(canRequestAnother())request(first,0);else deferredSegment=first;
        }

        void chunk(VideoPackets.SegmentChunk value){
            if(!key.identity().equals(value.identity())||manifest==null||value.requestId()!=requestId||value.segmentIndex()!=requestedSegment||value.totalChunks()<1)return;
            requestSentAt=System.currentTimeMillis();requestRetries=0;
            if(totalChunks==0){totalChunks=value.totalChunks();assembler.begin(value.identity(),value.requestId(),value.segmentIndex(),value.totalChunks(),value.segmentSha256(),value.presentationTimeMs(),value.keyframe());}
            Optional<VideoSegmentAssembler.CompletedSegment> completed=assembler.accept(value.identity(),value.requestId(),value.segmentIndex(),value.chunkIndex(),value.totalChunks(),value.segmentSha256(),value.presentationTimeMs(),value.keyframe(),value.data());
            TransferWindowFlow.Decision flow=TransferWindowFlow.afterChunk(value.chunkIndex(),currentWindowStart,8,totalChunks,completed.isPresent());
            if(completed.isPresent()){
                VideoSegmentAssembler.CompletedSegment complete=completed.get();ready.add(complete);readyBytes+=complete.byteLength();lastCompletedSegment=value.segmentIndex();requestedSegment=-1;requestSentAt=0;requestRetries=0;
                CinemarrNetwork.sendToServer(new VideoPayloads.SegmentAcknowledgement(new VideoPackets.SegmentAcknowledgement(value.identity(),value.requestId(),value.segmentIndex(),flow.receivedThroughChunk(),bufferedMs())));
                int local=descriptorIndex(value.segmentIndex());if(local>=0&&local+1<manifest.segments().size()){int next=manifest.segments().get(local+1).index();if(canRequestAnother())request(next,0);else deferredSegment=next;}else if(manifest.hasMore())CinemarrNetwork.sendToServer(new VideoPayloads.SegmentManifestRequest(new VideoPackets.SegmentManifestRequest(key.identity(),value.segmentIndex()+1)));else finalSegmentReceived=true;
            }else if(flow.continuesSegment()){
                CinemarrNetwork.sendToServer(new VideoPayloads.SegmentAcknowledgement(new VideoPackets.SegmentAcknowledgement(value.identity(),value.requestId(),value.segmentIndex(),flow.receivedThroughChunk(),bufferedMs())));
                request(value.segmentIndex(),flow.nextWindowStart());
            }
        }
        private void request(int segment,int firstChunk){
            int descriptor=descriptorIndex(segment);if(manifest==null||descriptor<0)return;
            if(firstChunk==0&&!withinPrefetchLead(manifest.segments().get(descriptor).presentationTimeMs(),CinemarrVideoPlayback.authoritativePositionMsLocal(session))){deferredSegment=segment;return;}
            deferredSegment=-1;requestedSegment=segment;currentWindowStart=firstChunk;if(firstChunk==0){totalChunks=0;requestId++;}requestSentAt=System.currentTimeMillis();
            CinemarrNetwork.sendToServer(new VideoPayloads.SegmentRequest(new VideoPackets.SegmentRequest(key.identity(),requestId,segment,firstChunk,8)));
        }
        void tick(long now){if(requestedSegment<0||requestSentAt==0||now-requestSentAt<1_500)return;if(requestRetries++<3){requestSentAt=now;CinemarrNetwork.sendToServer(new VideoPayloads.SegmentRequest(new VideoPackets.SegmentRequest(key.identity(),requestId,requestedSegment,currentWindowStart,8)));return;}assembler.reset();requestedSegment=-1;requestSentAt=0;requestRetries=0;if(manifest!=null&&!manifest.segments().isEmpty())CinemarrNetwork.sendToServer(new VideoPayloads.SegmentManifestRequest(new VideoPackets.SegmentManifestRequest(key.identity(),resumeSegment(manifest,now))));}
        static boolean withinPrefetchLead(long segmentPresentationTimeMs,long playbackPositionMs){return segmentPresentationTimeMs<=playbackPositionMs+ProtocolLimits.CLIENT_VIDEO_PREFETCH_LEAD_MS;}
        private int descriptorIndex(int segment){if(manifest==null)return -1;for(int index=0;index<manifest.segments().size();index++)if(manifest.segments().get(index).index()==segment)return index;return -1;}
        int resumeSegment(VideoPackets.SegmentManifest manifest,long localNow){int first=seekSegment(manifest,CinemarrVideoPlayback.authoritativePositionMsLocal(session,localNow));return first<0?-1:Math.max(first,lastCompletedSegment+1);}
        private static int seekSegment(VideoPackets.SegmentManifest manifest,long positionMs){int result=manifest.segments().isEmpty()?-1:manifest.segments().get(0).index();for(VideoPackets.SegmentDescriptor value:manifest.segments()){if(value.presentationTimeMs()>positionMs)break;if(value.keyframe())result=value.index();}return result;}
        private long bufferedMs(){long total=0;for(VideoSegmentAssembler.CompletedSegment segment:ready){int local=descriptorIndex(segment.segmentIndex());if(local>=0)total+=manifest.segments().get(local).durationMs();}return total;}
        private boolean canRequestAnother(){return SegmentPrefetchPolicy.allowsAnother(ready.size(),readyBytes);}
        private void clearReady(){ready.clear();readyBytes=0L;}
        boolean inputExhausted(){return finalSegmentReceived&&ready.isEmpty()&&requestedSegment<0&&deferredSegment<0;}
        void reset(){assembler.reset();clearReady();manifest=null;requestedSegment=-1;lastCompletedSegment=-1;currentWindowStart=0;totalChunks=0;deferredSegment=-1;requestSentAt=0;requestRetries=0;finalSegmentReceived=false;}
        VideoSegmentAssembler.CompletedSegment pollSegment(){if(deferredSegment>=0&&canRequestAnother())request(deferredSegment,0);VideoSegmentAssembler.CompletedSegment value=ready.poll();if(value!=null)readyBytes-=value.byteLength();if(deferredSegment>=0&&canRequestAnother())request(deferredSegment,0);return value;}
        VideoPackets.SegmentManifest manifest(){return manifest;}
    }
    private CinemarrVideoClientState(){}
}
