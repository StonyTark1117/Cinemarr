package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.screen.ScreenGeometry;
import stonytark.cinemarr.core.screen.ScreenLimits;
import stonytark.cinemarr.core.screen.ScreenPixel;
import stonytark.cinemarr.core.screen.ScreenTopology;
import stonytark.cinemarr.core.platform.CinemarrSettings;
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
    public static final int SCHEMA_VERSION = 1;
    private static final Factory<CinemarrWorldScreens> FACTORY = new Factory<>(CinemarrWorldScreens::new,
            CinemarrWorldScreens::load, null);
    private final Map<Long, Direction> pixels = new HashMap<>();
    private final Map<Long, Television> televisions = new HashMap<>();

    public static CinemarrWorldScreens get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "cinemarr_screens");
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

    public void removeController(BlockPos pos) { if (televisions.remove(pos.asLong()) != null) setDirty(); }

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
            ScreenGeometry geometry = ScreenTopology.analyze(values, limits());
            Television existing = televisions.get(controller.asLong());
            if (existing == null && ownedBy(owner) >= CinemarrSettings.maximumScreensPerOwner()) {
                return new Activation(false, "Maximum screens per owner reached");
            }
            UUID televisionId = existing == null ? UUID.randomUUID() : existing.id;
            UUID televisionOwner = existing == null ? owner : existing.owner;
            televisions.put(controller.asLong(), new Television(televisionId, televisionOwner, connected,
                    geometry.width(), geometry.height(), geometry.visibilityMask().toByteArray(), geometry.facing(),
                    geometry.minimumU(), geometry.minimumV(), existing == null ? PresentationMode.FIT : existing.presentationMode,
                    existing == null ? "" : existing.sessionName));
            setDirty();
            return new Activation(true, "Activated " + geometry.width() + "x" + geometry.height()
                    + " TV with " + geometry.pixelCount() + " visible pixels");
        } catch (IllegalArgumentException invalid) {
            return new Activation(false, invalid.getMessage());
        }
    }

    public Television television(BlockPos controller) { return televisions.get(controller.asLong()); }
    public int pixelCount() { return pixels.size(); }
    private int ownedBy(UUID owner) { int count=0; for(Television value:televisions.values())if(value.owner.equals(owner))count++; return count; }
    public void updatePresentation(BlockPos controller, PresentationMode mode) { Television value=televisions.get(controller.asLong()); if(value!=null&&mode!=null){value.presentationMode=mode;setDirty();} }
    public void updateSession(BlockPos controller, String name) { Television value=televisions.get(controller.asLong()); if(value!=null){value.sessionName=name==null?"":name.trim();setDirty();} }

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
        for (Long controller : invalid) televisions.remove(controller);
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
            Television tv = Television.load(value);
            if (tv != null) data.televisions.put(value.getLong("controller"), tv);
        }
        return data;
    }

    public static final class Television {
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
        Television(UUID id, UUID owner, Set<Long> pixels, int width, int height, byte[] mask, ScreenFacing facing,
                   int minimumU, int minimumV, PresentationMode presentationMode, String sessionName) {
            this.id = id; this.owner = owner; this.pixels = new HashSet<>(pixels); this.width = width; this.height = height;
            this.mask=mask==null?new byte[0]:mask.clone();this.facing=facing;this.minimumU=minimumU;this.minimumV=minimumV;
            this.presentationMode=presentationMode;this.sessionName=sessionName==null?"":sessionName;
        }
        public UUID id() { return id; }
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
        void save(CompoundTag tag) {
            tag.putUUID("id", id); tag.putUUID("owner", owner); tag.putInt("width", width); tag.putInt("height", height);
            tag.putByteArray("mask",mask);tag.putString("facing",facing.name());tag.putInt("minimumU",minimumU);tag.putInt("minimumV",minimumV);
            tag.putString("presentationMode",presentationMode.name());tag.putString("sessionName",sessionName);
            long[] values = new long[pixels.size()]; int index = 0; for (Long pixel : pixels) values[index++] = pixel; tag.putLongArray("pixels", values);
        }
        static Television load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("owner")) return null;
            Set<Long> pixels = new HashSet<>(); for (long pixel : tag.getLongArray("pixels")) pixels.add(pixel);
            try {
                ScreenFacing facing=ScreenFacing.valueOf(tag.getString("facing"));
                PresentationMode mode=tag.contains("presentationMode")?PresentationMode.valueOf(tag.getString("presentationMode")):PresentationMode.FIT;
                return new Television(tag.getUUID("id"), tag.getUUID("owner"), pixels, tag.getInt("width"), tag.getInt("height"),
                        tag.getByteArray("mask"),facing,tag.getInt("minimumU"),tag.getInt("minimumV"),mode,tag.getString("sessionName"));
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
