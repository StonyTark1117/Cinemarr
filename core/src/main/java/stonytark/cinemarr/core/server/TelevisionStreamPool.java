package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.video.TvDisplaySettings;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Per-TV media ownership, independent of watch-party clocks. All desired-state changes run on the server thread. */
public final class TelevisionStreamPool implements AutoCloseable {
    public interface Factory { VideoSessionCoordinator.MediaHandle start(Request request, UUID streamId, long generation) throws IOException; }
    public interface Work { CompletableFuture<Void> submit(Supplier<Void> operation); }
    public static final class Request {
        public final UUID televisionId;
        public final VideoSessionCoordinator.Snapshot timeline;
        public final TvDisplaySettings display;
        public final int width, height;
        public final Set<UUID> viewers;
        public Request(UUID tv, VideoSessionCoordinator.Snapshot timeline, TvDisplaySettings display, int width, int height, Set<UUID> viewers) {
            if(tv==null||timeline==null||display==null||width<1||height<1||viewers==null)throw new IllegalArgumentException("Invalid TV stream request");
            televisionId=tv;this.timeline=timeline;this.display=display;this.width=width;this.height=height;
            this.viewers=Collections.unmodifiableSet(new HashSet<UUID>(viewers));
        }
        boolean sameMedia(Request other) {
            return sameTimeline(other) && display.resolution().equals(other.display.resolution())
                    && width==other.width && height==other.height;
        }
        boolean sameTimeline(Request other) {
            return other != null && timeline.id().equals(other.timeline.id())
                    && timeline.playbackGeneration() == other.timeline.playbackGeneration()
                    && timeline.generation() == other.timeline.generation();
        }
        boolean eligible() { return timeline.item()!=null&&!timeline.paused()&&timeline.transcoding()&&!viewers.isEmpty(); }
    }
    private static final class Entry {
        Request desired, applied, starting, failed;
        Set<UUID> viewers = new HashSet<UUID>();
        boolean pending;
        String error="";
        Entry(Request request) { desired=request; }
    }
    private static final class StartContext {
        final Entry entry;
        final Request request;
        StartContext(Entry entry, Request request) { this.entry = entry; this.request = request; }
    }
    private final VideoSessionCoordinator streams;
    private final int maximumStreams;
    private final Factory factory;
    private final Work work;
    private final ThreadLocal<StartContext> starting = new ThreadLocal<StartContext>();
    private final Map<UUID,Entry> entries = new LinkedHashMap<UUID,Entry>();
    private final LinkedHashSet<UUID> waiting = new LinkedHashSet<UUID>();
    private boolean closed;
    private long changes;

    public TelevisionStreamPool(int maximumStreams, long graceMs, Factory factory, Work work) {
        if (factory == null || work == null) throw new IllegalArgumentException("TV stream factory and work queue are required");
        this.maximumStreams=maximumStreams;this.factory=factory;this.work=work;
        streams=new VideoSessionCoordinator(maximumStreams,graceMs,(id,generation,item,offset)->startMedia(id,generation),true);
    }

    private boolean desired(StartContext context) {
        return !closed && entries.get(context.request.televisionId) == context.entry
                && context.entry.desired.sameMedia(context.request) && context.entry.desired.eligible();
    }

    private VideoSessionCoordinator.MediaHandle startMedia(UUID id, long generation) throws IOException {
        StartContext context = starting.get();
        synchronized (this) {
            if (context == null || !desired(context)) throw new IllegalStateException("TV stream request changed before preparation");
        }
        VideoSessionCoordinator.MediaHandle media = factory.start(context.request, id, generation);
        synchronized (this) {
            if (entries.get(context.request.televisionId) == context.entry && !desired(context)) {
                streams.cancelPendingStart(context.request.televisionId.toString());
            }
        }
        // Return even a superseded handle: the coordinator owns its rejection
        // and bounded retirement. Throwing here would lose that ownership.
        return media;
    }
    public synchronized void update(Request request, long now) throws IOException {
        if(closed)return;
        Entry entry=entries.get(request.televisionId);
        if(entry==null){entry=new Entry(request);entries.put(request.televisionId,entry);streams.tune(request.televisionId,request.televisionId.toString());changes++;}
        Request previous=entry.desired;entry.desired=request;
        String name=request.televisionId.toString();
        boolean viewersChanged = !entry.viewers.equals(request.viewers);
        for(UUID viewer:entry.viewers)if(!request.viewers.contains(viewer))streams.viewerLeft(name,viewer,now);
        for(UUID viewer:request.viewers)if(!entry.viewers.contains(viewer))streams.viewerEntered(name,viewer);
        entry.viewers=new HashSet<UUID>(request.viewers);
        // A newly visible client needs a fresh manifest even when the media
        // generation and display settings are unchanged. The server publishes
        // session state when this revision advances; without it, a follower
        // joining an already active stream receives PLAYING state but no data.
        if (viewersChanged) changes++;
        if(!request.sameMedia(previous)){entry.failed=null;entry.error="";changes++;}
        if (entry.pending && (!request.sameMedia(entry.starting) || !request.eligible())) {
            if (!request.sameTimeline(entry.starting) || request.timeline.item() == null
                    || request.timeline.paused() || !request.timeline.transcoding()) {
                streams.suspend(name, now);
            } else {
                // A quality change invalidates only the replacement. Viewer
                // loss likewise leaves working media under the normal grace
                // period established by viewerLeft above.
                streams.cancelPendingStart(name);
            }
        }
        if(!request.eligible()) {
            waiting.remove(request.televisionId);
            if(request.timeline.item()==null)streams.stop(name,now);
            else if(request.timeline.paused())streams.pause(name,now);
            else if(!request.timeline.transcoding())streams.suspend(name,now);
            return;
        }
        VideoSessionCoordinator.Snapshot current=streams.snapshot(name,now);
        if((!current.transcoding()||!request.sameMedia(entry.applied))&&!entry.pending&&!request.sameMedia(entry.failed))waiting.add(request.televisionId);
    }
    public synchronized void retain(Set<UUID> televisions) throws IOException {
        Iterator<Map.Entry<UUID,Entry>> it=entries.entrySet().iterator();
        while(it.hasNext()){Map.Entry<UUID,Entry> entry=it.next();if(!televisions.contains(entry.getKey())){streams.untune(entry.getKey());waiting.remove(entry.getKey());it.remove();changes++;}}
    }
    public synchronized void tick(long now) throws IOException {
        if(closed)return;
        streams.tick(now);
        int reserved=streams.activeStreamCount();
        for(Map.Entry<UUID,Entry> e:entries.entrySet())if(e.getValue().pending&&!streams.snapshot(e.getKey().toString(),now).transcoding())reserved++;
        Iterator<UUID> it=waiting.iterator();
        while(it.hasNext()) {
            UUID tv=it.next();Entry entry=entries.get(tv);
            if(entry==null||!entry.desired.eligible()){it.remove();continue;}
            VideoSessionCoordinator.Snapshot current=streams.snapshot(tv.toString(),now);
            if(!current.transcoding()&&reserved>=maximumStreams)continue;
            if(streams.activeStreamCount()+streams.pendingStarts()+streams.retiringMedia()>=maximumStreams*2)break;
            Request request=entry.desired;entry.pending=true;entry.starting=request;it.remove();changes++;
            if(!current.transcoding())reserved++;
            final Entry target=entry;
            Supplier<Void> operation = ()->{
                try {
                    Request latest;
                    synchronized(TelevisionStreamPool.this){
                        if(closed||entries.get(tv)!=target||!target.desired.sameMedia(request)||!target.desired.eligible())return null;
                        latest=target.desired;
                    }
                    starting.set(new StartContext(target, latest));
                    streams.play(tv.toString(),latest.timeline.item(),latest.timeline.positionMs(),latest.timeline.serverEpochMs(),current.generation());
                    synchronized(TelevisionStreamPool.this){if(entries.get(tv)==target){target.applied=latest;target.error="";}}
                    return null;
                } catch(IOException error){throw new java.util.concurrent.CompletionException(error);}
                finally{starting.remove();}
            };
            CompletableFuture<Void> submitted;
            try {
                submitted = work.submit(operation);
                if (submitted == null) throw new IllegalStateException("TV work queue returned no completion");
            } catch (RuntimeException rejection) {
                finishStart(tv, target, request, rejection);
                continue;
            }
            submitted.whenComplete((unused, failure) -> finishStart(tv, target, request, failure));
        }
    }
    private synchronized void finishStart(UUID tv, Entry target, Request request, Throwable failure) {
        target.pending=false;target.starting=null;changes++;
        if(entries.get(tv)!=target||closed)return;
        if(failure!=null&&target.desired.sameMedia(request)&&target.desired.eligible()){
            target.failed=request;target.error="Unable to prepare TV stream; change resolution or restart playback to retry";
        }
    }
    public synchronized VideoSessionCoordinator.Snapshot snapshot(UUID tv,long now) { return entries.containsKey(tv)?streams.snapshot(tv.toString(),now):null; }
    public synchronized Request request(UUID tv) { Entry entry=entries.get(tv);return entry==null?null:entry.desired; }
    public synchronized String message(UUID tv) {
        Entry entry=entries.get(tv);if(entry==null)return "";
        return !entry.error.isEmpty()?entry.error:entry.pending?"Preparing TV stream":waiting.contains(tv)?"Waiting for stream capacity":"";
    }
    public synchronized long changes(){return changes;}
    public int activeStreamCount(){return streams.activeStreamCount();}
    public int pendingStarts(){return streams.pendingStarts();}
    public int retiringMedia(){return streams.retiringMedia();}
    public long closeFailures(){return streams.closeFailures();}
    public VideoSessionCoordinator.Snapshot snapshotIfPresent(UUID id,long generation,long now){return streams.snapshotIfPresent(id,generation,now);}
    public boolean isViewer(UUID id,long generation,UUID viewer){return streams.isViewer(id,generation,viewer);}
    /** Only published media for the current desired timeline can authorize transport. */
    public synchronized VideoStreamIdentity identity(UUID id, long generation) {
        VideoSessionCoordinator.Snapshot stream = streams.snapshotIfPresent(id, generation, 0);
        if (stream == null || !stream.transcoding()) return null;
        for (UUID television : stream.televisions()) {
            Entry entry = entries.get(television);
            if (entry != null && entry.applied != null && entry.desired.sameTimeline(entry.applied))
                return new VideoStreamIdentity(entry.applied.timeline.id(), entry.applied.timeline.generation(), id, generation);
        }
        return null;
    }
    public synchronized boolean isViewer(VideoStreamIdentity identity, UUID viewer) {
        return identity != null && identity.equals(identity(identity.streamId(), identity.streamGeneration()))
                && streams.isViewer(identity.streamId(), identity.streamGeneration(), viewer);
    }
    public boolean isSupersededViewer(UUID id,long generation,UUID viewer){return streams.isSupersededViewer(id,generation,viewer);}
    @Override public void close() throws IOException {
        synchronized(this){closed=true;waiting.clear();entries.clear();}
        streams.close();
    }
}
