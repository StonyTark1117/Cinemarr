package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.screen.ScreenGeometry;
import stonytark.cinemarr.core.screen.ScreenLimits;
import stonytark.cinemarr.core.screen.ScreenPixel;
import stonytark.cinemarr.core.screen.ScreenTopology;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dimension-local durable index of pixels and activated televisions. */
public final class CinemarrWorldScreens extends SavedData {
    public static final int SCHEMA_VERSION = 2;
    private static final Factory<CinemarrWorldScreens> FACTORY = new Factory<>(CinemarrWorldScreens::new,
            CinemarrWorldScreens::load, null);
    private final Map<Long, Direction> pixels = new HashMap<>();
    private final Map<Long, Television> televisions = new HashMap<>();
    private final transient String detachedDimension = "detached:" + UUID.randomUUID();
    private transient ServerLevel level;
    private transient boolean registrationsReconciled;

    public static CinemarrWorldScreens get(ServerLevel level) {
        CinemarrWorldScreens value = level.getDataStorage().computeIfAbsent(FACTORY, "cinemarr_screens");
        value.level = level;
        if(!value.registrationsReconciled)value.reconcileRegistrations();
        return value;
    }

    public void putPixel(BlockPos pos, Direction facing) {
        pixels.put(pos.asLong(), facing);
        invalidateContaining(pos.asLong());
        setDirty();
    }

    public void removePixel(BlockPos pos) {
        pixels.remove(pos.asLong());
        invalidateContaining(pos.asLong());
        setDirty();
    }

    public Television removeController(BlockPos pos) {
        Television removed = televisions.get(pos.asLong());
        if (removed != null) {
            TelevisionLifecycle.unregister(removed.id, removed.sessionName);
            if (television(removed.id) != null) removeLocal(removed.id);
        }
        return removed;
    }

    public Activation activate(BlockPos controller, UUID owner) {
        BlockPos start = null;
        for (Direction direction : Direction.values()) if (pixels.containsKey(controller.relative(direction).asLong())) {
            start = controller.relative(direction); break;
        }
        if (start == null) return new Activation(false, "TV Controller must touch a Screen Pixel");
        Set<Long> connected = connected(start);
        List<ScreenPixel> values = new ArrayList<>(connected.size());
        for (Long packed : connected) {
            BlockPos pos = BlockPos.of(packed);
            values.add(new ScreenPixel(pos.getX(), pos.getY(), pos.getZ(), facing(pixels.get(packed))));
        }
        try {
            ScreenGeometry geometry = ScreenTopology.analyze(values, limits(), CinemarrSettings.allowIrregularScreens());
            Television existing = televisions.get(controller.asLong());
            for (Map.Entry<Long, Television> entry : televisions.entrySet()) if (entry.getKey().longValue() != controller.asLong()
                    && intersects(entry.getValue().pixels, connected)) return new Activation(false, "Screen pixels already belong to another TV");
            UUID televisionId = existing == null ? UUID.randomUUID() : existing.id;
            UUID televisionOwner = existing == null ? owner : existing.owner;
            if (TelevisionLifecycle.overlaps(dimensionKey(), connected, televisionId)) {
                return new Activation(false, "Screen pixels already belong to another TV");
            }
            Television activated = new Television(controller.asLong(), televisionId, televisionOwner, connected,
                    geometry.width(), geometry.height(), geometry.visibilityMask().toByteArray(), geometry.facing(),
                    geometry.minimumU(), geometry.minimumV(), existing == null ? PresentationMode.FIT : existing.presentationMode,
                    existing == null ? "" : existing.sessionName, geometry.width(), geometry.height());
            if (!TelevisionLifecycle.register(registration(activated), CinemarrSettings.maximumScreensPerOwner())) {
                return new Activation(false, existing == null ? "Maximum screens per owner reached" : "TV registration conflicts with another screen");
            }
            televisions.put(controller.asLong(), activated);
            setDirty();
            return new Activation(true, "Activated " + geometry.width() + "x" + geometry.height()
                    + " TV with " + geometry.pixelCount() + " visible pixels");
        } catch (IllegalArgumentException invalid) {
            return new Activation(false, invalid.getMessage());
        }
    }

    public Television television(BlockPos controller) { return televisions.get(controller.asLong()); }
    public Television television(UUID id) { for(Television value:televisions.values())if(value.id.equals(id))return value;return null; }
    public Television removeTelevision(UUID id) { Television value=television(id);return value==null?null:removeController(BlockPos.of(value.controllerPos)); }
    public List<Television> televisions(){return java.util.Collections.unmodifiableList(new ArrayList<>(televisions.values()));}
    public List<Television> televisionsForChunk(ChunkPos chunk) {
        List<Television> found = new ArrayList<>();
        for (Television television : televisions.values()) {
            for (Long packed : television.pixels) {
                BlockPos pixel = BlockPos.of(packed);
                if (pixel.getX() >> 4 == chunk.x && pixel.getZ() >> 4 == chunk.z) { found.add(television); break; }
            }
        }
        return found;
    }
    public int pixelCount() { return pixels.size(); }
    public int pruneInvalid(){
        int removed=0;
        for(Television television:new ArrayList<>(televisions.values())){
            LiveValidation validation=liveValidation(television);
            if(validation==LiveValidation.INVALID){if(TelevisionLifecycle.unregister(television.id,television.sessionName))removed++;else{removeLocal(television.id);removed++;}}
            else TelevisionLifecycle.validation(television.id,validation==LiveValidation.LOADED
                    ?TelevisionLifecycle.Validation.LOADED:TelevisionLifecycle.Validation.SAVED);
        }
        return removed;
    }
    public boolean overlaps(BlockPos controller, Set<Long> candidates) {
        for (Map.Entry<Long, Television> entry : televisions.entrySet()) {
            if (entry.getKey().longValue() != controller.asLong() && intersects(entry.getValue().pixels, candidates)) return true;
        }
        return false;
    }
    public void updatePresentation(BlockPos controller, PresentationMode mode) { Television value=televisions.get(controller.asLong()); if(value!=null&&mode!=null){value.presentationMode=mode;setDirty();} }
    public void updateSession(BlockPos controller, String name) { Television value=televisions.get(controller.asLong()); if(value!=null){value.sessionName=name==null?"":name.trim();TelevisionLifecycle.session(value.id,value.sessionName);setDirty();} }
    public void updateRendition(BlockPos controller, int width, int height) {
        Television value = televisions.get(controller.asLong());
        if (value != null && width > 0 && height > 0) { value.renditionWidth = width; value.renditionHeight = height; setDirty(); }
    }

    private Set<Long> connected(BlockPos start) {
        Direction facing = pixels.get(start.asLong());
        int plane = plane(start, facing);
        Set<Long> found = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty() && found.size() <= CinemarrSettings.maximumScreenPixels()) {
            BlockPos current = pending.removeFirst();
            long packed = current.asLong();
            Direction currentFacing = pixels.get(packed);
            if (currentFacing != facing || plane(current, facing) != plane || !found.add(packed)) continue;
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() != facing.getAxis()) pending.add(current.relative(direction));
            }
        }
        return found;
    }

    private void invalidateContaining(long pixel) {
        List<Long> invalid = new ArrayList<>();
        for (Map.Entry<Long, Television> entry : televisions.entrySet()) if (entry.getValue().pixels.contains(pixel)) invalid.add(entry.getKey());
        for (Long controller : invalid) removeController(BlockPos.of(controller));
    }
    void reconcileRegistrations() {
        registrationsReconciled=true;
        List<Long> invalid = new ArrayList<>();
        Set<Long> occupied = new HashSet<>();
        for (Map.Entry<Long, Television> entry : televisions.entrySet()) {
            Television television = entry.getValue();
            if (!valid(television) || intersects(occupied,television.pixels)
                    || !TelevisionLifecycle.restore(registration(television))) invalid.add(entry.getKey());
            else occupied.addAll(television.pixels);
        }
        for (Long controller : invalid) removeLocalAt(controller);
        if (!invalid.isEmpty()) setDirty();
    }
    private boolean valid(Television television) {
        if (television.pixels.isEmpty()) return false;
        List<ScreenPixel> values = new ArrayList<>(television.pixels.size());
        for (Long packed : television.pixels) {
            Direction direction = pixels.get(packed);
            if (direction == null) return false;
            BlockPos pos = BlockPos.of(packed);
            values.add(new ScreenPixel(pos.getX(), pos.getY(), pos.getZ(), facing(direction)));
        }
        try {
            ScreenGeometry geometry = ScreenTopology.analyze(values, limits(), CinemarrSettings.allowIrregularScreens());
            return geometry.width() == television.width && geometry.height() == television.height;
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private LiveValidation liveValidation(Television television){
        if(level==null)return LiveValidation.UNLOADED;
        BlockPos controller=BlockPos.of(television.controllerPos);
        if(!level.hasChunkAt(controller))return LiveValidation.UNLOADED;
        for(Long packed:television.pixels)if(!level.hasChunkAt(BlockPos.of(packed)))return LiveValidation.UNLOADED;
        net.minecraft.world.level.block.Block controllerBlock=level.getBlockState(controller).getBlock();
        if(!(controllerBlock instanceof TvControllerBlock)&&!(controllerBlock instanceof QuickTvBlock))return LiveValidation.INVALID;
        for(Long packed:television.pixels){BlockPos pos=BlockPos.of(packed);BlockStateView state=blockState(pos);if(!state.pixel||state.facing!=Direction.valueOf(television.facing.name()))return LiveValidation.INVALID;}
        return valid(television)?LiveValidation.LOADED:LiveValidation.INVALID;
    }
    private BlockStateView blockState(BlockPos pos){net.minecraft.world.level.block.state.BlockState state=level.getBlockState(pos);return new BlockStateView(state.getBlock() instanceof ScreenPixelBlock,state.getBlock() instanceof ScreenPixelBlock?state.getValue(ScreenPixelBlock.FACING):null);}
    private TelevisionLifecycle.Registration registration(final Television television){BlockPos controller=BlockPos.of(television.controllerPos);return new TelevisionLifecycle.Registration(television.id,television.owner,dimensionKey(),television.controllerPos,controller.getX(),controller.getY(),controller.getZ(),television.pixels,television.sessionName,TelevisionLifecycle.Validation.SAVED,()->removeLocal(television.id));}
    private String dimensionKey(){return level==null?detachedDimension:String.valueOf(level.dimension());}
    private Television removeLocal(UUID id){Television value=television(id);if(value!=null){televisions.remove(value.controllerPos);setDirty();}return value;}
    private Television removeLocalAt(long controller){Television value=televisions.remove(controller);if(value!=null)setDirty();return value;}
    private enum LiveValidation{LOADED,UNLOADED,INVALID}
    private static final class BlockStateView{final boolean pixel;final Direction facing;BlockStateView(boolean pixel,Direction facing){this.pixel=pixel;this.facing=facing;}}
    private static boolean intersects(Set<Long> first, Set<Long> second) {
        Set<Long> smaller = first.size() <= second.size() ? first : second;
        Set<Long> larger = smaller == first ? second : first;
        for (Long value : smaller) if (larger.contains(value)) return true;
        return false;
    }
    private static int plane(BlockPos pos, Direction facing) {
        switch (facing.getAxis()) { case X: return pos.getX(); case Y: return pos.getY(); default: return pos.getZ(); }
    }
    private static ScreenFacing facing(Direction value) { return ScreenFacing.valueOf(value.name()); }
    private static ScreenLimits limits() {
        return new ScreenLimits(CinemarrSettings.minimumScreenPixels(), CinemarrSettings.maximumScreenPixels(),
                CinemarrSettings.maximumScreenDimension());
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        ListTag pixelTags = new ListTag();
        for (Map.Entry<Long, Direction> entry : pixels.entrySet()) {
            CompoundTag value = new CompoundTag(); value.putLong("pos", entry.getKey()); value.putString("facing", entry.getValue().name()); pixelTags.add(value);
        }
        tag.put("pixels", pixelTags);
        ListTag televisionTags = new ListTag();
        for (Map.Entry<Long, Television> entry : televisions.entrySet()) {
            CompoundTag value = new CompoundTag(); value.putLong("controller", entry.getKey()); entry.getValue().save(value); televisionTags.add(value);
        }
        tag.put("televisions", televisionTags);
        return tag;
    }

    public static CinemarrWorldScreens load(CompoundTag tag, HolderLookup.Provider registries) {
        CinemarrWorldScreens data = new CinemarrWorldScreens();
        ListTag pixelTags = tag.getList("pixels", Tag.TAG_COMPOUND);
        for (int index = 0; index < pixelTags.size(); index++) {
            CompoundTag value = pixelTags.getCompound(index);
            try { data.pixels.put(value.getLong("pos"), Direction.valueOf(value.getString("facing"))); }
            catch (IllegalArgumentException ignored) {}
        }
        ListTag televisionTags = tag.getList("televisions", Tag.TAG_COMPOUND);
        for (int index = 0; index < televisionTags.size(); index++) {
            CompoundTag value = televisionTags.getCompound(index);
            Television tv = Television.load(value, value.getLong("controller"));
            if (tv != null) data.televisions.put(value.getLong("controller"), tv);
        }
        return data;
    }

    public static final class Television {
        private final long controllerPos;
        private final UUID id;
        private final UUID owner;
        private final Set<Long> pixels;
        private final int width;
        private final int height;
        private final byte[] mask;
        private final ScreenFacing facing;
        private final int minimumU;
        private final int minimumV;
        private PresentationMode presentationMode;
        private String sessionName;
        private int renditionWidth;
        private int renditionHeight;
        Television(long controllerPos, UUID id, UUID owner, Set<Long> pixels, int width, int height, byte[] mask, ScreenFacing facing,
                   int minimumU, int minimumV, PresentationMode presentationMode, String sessionName,
                   int renditionWidth, int renditionHeight) {
            this.controllerPos=controllerPos;this.id = id; this.owner = owner; this.pixels = new HashSet<>(pixels); this.width = width; this.height = height;
            this.mask=mask==null?new byte[0]:mask.clone();this.facing=facing;this.minimumU=minimumU;this.minimumV=minimumV;
            this.presentationMode=presentationMode;this.sessionName=sessionName==null?"":sessionName;
            this.renditionWidth=renditionWidth>0?renditionWidth:width;this.renditionHeight=renditionHeight>0?renditionHeight:height;
        }
        public UUID id() { return id; }
        public long controllerPos() { return controllerPos; }
        public UUID owner() { return owner; }
        public Set<Long> pixels() { return java.util.Collections.unmodifiableSet(pixels); }
        public int width() { return width; }
        public int height() { return height; }
        public byte[] mask() { return mask.clone(); }
        public ScreenFacing facing() { return facing; }
        public int minimumU() { return minimumU; }
        public int minimumV() { return minimumV; }
        public int plane() {
            if (pixels.isEmpty()) throw new IllegalStateException("Television has no pixels");
            return CinemarrWorldScreens.plane(BlockPos.of(pixels.iterator().next()), Direction.valueOf(facing.name()));
        }
        public PresentationMode presentationMode() { return presentationMode; }
        public String sessionName() { return sessionName; }
        public int renditionWidth() { return renditionWidth; }
        public int renditionHeight() { return renditionHeight; }
        void save(CompoundTag tag) {
            tag.putUUID("id", id); tag.putUUID("owner", owner); tag.putInt("width", width); tag.putInt("height", height);
            tag.putByteArray("mask",mask);tag.putString("facing",facing.name());tag.putInt("minimumU",minimumU);tag.putInt("minimumV",minimumV);
            tag.putString("presentationMode",presentationMode.name());tag.putString("sessionName",sessionName);
            tag.putInt("renditionWidth",renditionWidth);tag.putInt("renditionHeight",renditionHeight);
            long[] values = new long[pixels.size()]; int index = 0; for (Long pixel : pixels) values[index++] = pixel; tag.putLongArray("pixels", values);
        }
        static Television load(CompoundTag tag, long controllerPos) {
            if (!tag.hasUUID("id") || !tag.hasUUID("owner")) return null;
            Set<Long> pixels = new HashSet<>(); for (long pixel : tag.getLongArray("pixels")) pixels.add(pixel);
            try {
                ScreenFacing facing=ScreenFacing.valueOf(tag.getString("facing"));
                PresentationMode mode=tag.contains("presentationMode")?PresentationMode.valueOf(tag.getString("presentationMode")):PresentationMode.FIT;
                return new Television(controllerPos, tag.getUUID("id"), tag.getUUID("owner"), pixels, tag.getInt("width"), tag.getInt("height"),
                        tag.getByteArray("mask"),facing,tag.getInt("minimumU"),tag.getInt("minimumV"),mode,tag.getString("sessionName"),
                        tag.contains("renditionWidth")?tag.getInt("renditionWidth"):tag.getInt("width"),
                        tag.contains("renditionHeight")?tag.getInt("renditionHeight"):tag.getInt("height"));
            } catch(IllegalArgumentException invalid){return null;}
        }
    }

    public static final class Activation {
        private final boolean success;
        private final String message;
        Activation(boolean success, String message) { this.success = success; this.message = message; }
        public boolean success() { return success; }
        public String message() { return message; }
    }
}
