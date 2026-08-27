package stonytark.cinemarr.screen;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.screen.ScreenGeometry;
import stonytark.cinemarr.core.screen.ScreenLimits;
import stonytark.cinemarr.core.screen.ScreenPixel;
import stonytark.cinemarr.core.screen.ScreenTopology;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dimension-local pixel and television index for Forge 1.7.10. */
public final class LegacyWorldScreens extends WorldSavedData {
    public static final String DATA_NAME = "cinemarr_screens";
    public static final int SCHEMA_VERSION = 2;
    private final Map<Long, ScreenFacing> pixels = new HashMap<Long, ScreenFacing>();
    private final Map<Long, Television> televisions = new HashMap<Long, Television>();

    public LegacyWorldScreens() { this(DATA_NAME); }
    public LegacyWorldScreens(String name) { super(name); }

    public static LegacyWorldScreens get(WorldServer world) {
        MapStorage storage = world.mapStorage;
        LegacyWorldScreens value = (LegacyWorldScreens) storage.loadData(LegacyWorldScreens.class, DATA_NAME);
        if (value == null) {
            value = new LegacyWorldScreens(DATA_NAME);
            storage.setData(DATA_NAME, value);
            value.markDirty();
        }
        return value;
    }

    public void putPixel(int x, int y, int z, ScreenFacing facing) {
        long packed = LegacyBlockPos.pack(x, y, z);
        pixels.put(packed, facing);
        invalidateContaining(packed);
        markDirty();
    }

    public void removePixel(int x, int y, int z) {
        long packed = LegacyBlockPos.pack(x, y, z);
        pixels.remove(packed);
        invalidateContaining(packed);
        markDirty();
    }

    public Television removeController(int x, int y, int z) {
        Television removed = televisions.remove(LegacyBlockPos.pack(x, y, z));
        if (removed != null) markDirty();
        return removed;
    }

    public Activation activate(int x, int y, int z, UUID owner) {
        long controller = LegacyBlockPos.pack(x, y, z);
        long start = 0L;
        boolean foundStart = false;
        for (int side = 0; side < 6; side++) {
            long candidate = offset(controller, side);
            if (pixels.containsKey(candidate)) { start = candidate; foundStart = true; break; }
        }
        if (!foundStart) return new Activation(false, "TV Controller must touch a Screen Pixel", null);
        Set<Long> connected = connected(start);
        List<ScreenPixel> values = new ArrayList<ScreenPixel>(connected.size());
        for (Long packed : connected) values.add(new ScreenPixel(LegacyBlockPos.x(packed), LegacyBlockPos.y(packed),
                LegacyBlockPos.z(packed), pixels.get(packed)));
        try {
            ScreenGeometry geometry = ScreenTopology.analyze(values, new ScreenLimits(CinemarrSettings.minimumScreenPixels(),
                    CinemarrSettings.maximumScreenPixels(), CinemarrSettings.maximumScreenDimension()));
            Television existing = televisions.get(controller);
            if (existing == null && ownedBy(owner) >= CinemarrSettings.maximumScreensPerOwner()) {
                return new Activation(false, "Maximum screens per owner reached", null);
            }
            Television television = new Television(controller, existing == null ? UUID.randomUUID() : existing.id,
                    existing == null ? owner : existing.owner, connected, geometry.width(), geometry.height(),
                    geometry.visibilityMask().toByteArray(), geometry.facing(), geometry.minimumU(), geometry.minimumV(),
                    existing == null ? PresentationMode.FIT : existing.presentationMode,
                    existing == null ? "" : existing.sessionName);
            televisions.put(controller, television);
            markDirty();
            return new Activation(true, "Activated " + geometry.width() + "x" + geometry.height()
                    + " TV with " + geometry.pixelCount() + " visible pixels", television);
        } catch (IllegalArgumentException invalid) {
            return new Activation(false, invalid.getMessage(), null);
        }
    }

    public Television television(long controller) { return televisions.get(controller); }
    public List<Television> televisions() { return Collections.unmodifiableList(new ArrayList<Television>(televisions.values())); }
    public List<Television> televisionsForChunk(int chunkX, int chunkZ) {
        List<Television> values = new ArrayList<Television>();
        for (Television television : televisions.values()) for (Long pixel : television.pixels) {
            if ((LegacyBlockPos.x(pixel) >> 4) == chunkX && (LegacyBlockPos.z(pixel) >> 4) == chunkZ) {
                values.add(television); break;
            }
        }
        return values;
    }
    public void updatePresentation(long controller, PresentationMode mode) {
        Television value = televisions.get(controller);
        if (value != null && mode != null) { value.presentationMode = mode; markDirty(); }
    }
    public void updateSession(long controller, String name) {
        Television value = televisions.get(controller);
        if (value != null) { value.sessionName = name == null ? "" : name.trim(); markDirty(); }
    }

    private int ownedBy(UUID owner) {
        int count = 0;
        for (Television value : televisions.values()) if (value.owner.equals(owner)) count++;
        return count;
    }

    private Set<Long> connected(long start) {
        ScreenFacing facing = pixels.get(start);
        int plane = plane(start, facing);
        Set<Long> found = new HashSet<Long>();
        ArrayDeque<Long> pending = new ArrayDeque<Long>();
        pending.add(start);
        while (!pending.isEmpty() && found.size() <= CinemarrSettings.maximumScreenPixels()) {
            long current = pending.removeFirst();
            ScreenFacing currentFacing = pixels.get(current);
            if (currentFacing != facing || plane(current, facing) != plane || !found.add(current)) continue;
            for (int side = 0; side < 6; side++) if (axis(side) != facingAxis(facing)) pending.add(offset(current, side));
        }
        return found;
    }

    private void invalidateContaining(long pixel) {
        List<Long> invalid = new ArrayList<Long>();
        for (Map.Entry<Long, Television> entry : televisions.entrySet()) if (entry.getValue().pixels.contains(pixel)) invalid.add(entry.getKey());
        for (Long controller : invalid) televisions.remove(controller);
    }

    private static int plane(long packed, ScreenFacing facing) {
        int axis = facingAxis(facing);
        return axis == 0 ? LegacyBlockPos.x(packed) : axis == 1 ? LegacyBlockPos.y(packed) : LegacyBlockPos.z(packed);
    }
    private static int facingAxis(ScreenFacing facing) {
        return facing == ScreenFacing.EAST || facing == ScreenFacing.WEST ? 0
                : facing == ScreenFacing.UP || facing == ScreenFacing.DOWN ? 1 : 2;
    }
    private static int axis(int side) { return side < 2 ? 1 : side < 4 ? 2 : 0; }
    private static long offset(long packed, int side) {
        int x = LegacyBlockPos.x(packed), y = LegacyBlockPos.y(packed), z = LegacyBlockPos.z(packed);
        if (side == 0) y--; else if (side == 1) y++; else if (side == 2) z--;
        else if (side == 3) z++; else if (side == 4) x--; else x++;
        return LegacyBlockPos.pack(x, y, z);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        pixels.clear(); televisions.clear();
        NBTTagList pixelTags = tag.getTagList("pixels", 10);
        for (int index = 0; index < pixelTags.tagCount(); index++) {
            NBTTagCompound value = pixelTags.getCompoundTagAt(index);
            try { pixels.put(value.getLong("pos"), ScreenFacing.valueOf(value.getString("facing"))); }
            catch (IllegalArgumentException ignored) {}
        }
        NBTTagList televisionTags = tag.getTagList("televisions", 10);
        for (int index = 0; index < televisionTags.tagCount(); index++) {
            NBTTagCompound value = televisionTags.getCompoundTagAt(index);
            Television television = Television.load(value);
            if (television != null) televisions.put(television.controllerPos, television);
        }
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("schemaVersion", SCHEMA_VERSION);
        NBTTagList pixelTags = new NBTTagList();
        for (Map.Entry<Long, ScreenFacing> entry : pixels.entrySet()) {
            NBTTagCompound value = new NBTTagCompound(); value.setLong("pos", entry.getKey());
            value.setString("facing", entry.getValue().name()); pixelTags.appendTag(value);
        }
        tag.setTag("pixels", pixelTags);
        NBTTagList televisionTags = new NBTTagList();
        for (Television television : televisions.values()) { NBTTagCompound value = new NBTTagCompound(); television.save(value); televisionTags.appendTag(value); }
        tag.setTag("televisions", televisionTags);
    }

    public static final class Television {
        private final long controllerPos;
        private final UUID id;
        private final UUID owner;
        private final Set<Long> pixels;
        private final int width, height;
        private final byte[] mask;
        private final ScreenFacing facing;
        private final int minimumU, minimumV;
        private PresentationMode presentationMode;
        private String sessionName;

        Television(long controllerPos, UUID id, UUID owner, Set<Long> pixels, int width, int height, byte[] mask,
                   ScreenFacing facing, int minimumU, int minimumV, PresentationMode mode, String sessionName) {
            this.controllerPos = controllerPos; this.id = id; this.owner = owner; this.pixels = new HashSet<Long>(pixels);
            this.width = width; this.height = height; this.mask = mask == null ? new byte[0] : mask.clone();
            this.facing = facing; this.minimumU = minimumU; this.minimumV = minimumV;
            this.presentationMode = mode; this.sessionName = sessionName == null ? "" : sessionName;
        }

        public long controllerPos() { return controllerPos; }
        public UUID id() { return id; }
        public UUID owner() { return owner; }
        public Set<Long> pixels() { return Collections.unmodifiableSet(pixels); }
        public int width() { return width; }
        public int height() { return height; }
        public byte[] mask() { return mask.clone(); }
        public ScreenFacing facing() { return facing; }
        public int minimumU() { return minimumU; }
        public int minimumV() { return minimumV; }
        public int plane() { return LegacyWorldScreens.plane(pixels.iterator().next(), facing); }
        public PresentationMode presentationMode() { return presentationMode; }
        public String sessionName() { return sessionName; }

        void save(NBTTagCompound tag) {
            tag.setLong("controller", controllerPos); tag.setLong("idMost", id.getMostSignificantBits());
            tag.setLong("idLeast", id.getLeastSignificantBits()); tag.setLong("ownerMost", owner.getMostSignificantBits());
            tag.setLong("ownerLeast", owner.getLeastSignificantBits()); tag.setInteger("width", width); tag.setInteger("height", height);
            tag.setByteArray("mask", mask); tag.setString("facing", facing.name()); tag.setInteger("minimumU", minimumU);
            tag.setInteger("minimumV", minimumV); tag.setString("presentationMode", presentationMode.name());
            tag.setString("sessionName", sessionName); NBTTagList positions = new NBTTagList();
            for (Long pixel : pixels) { NBTTagCompound value = new NBTTagCompound(); value.setLong("pos", pixel); positions.appendTag(value); }
            tag.setTag("screenPixels", positions);
        }

        static Television load(NBTTagCompound tag) {
            Set<Long> pixels = new HashSet<Long>(); NBTTagList positions = tag.getTagList("screenPixels", 10);
            for (int index = 0; index < positions.tagCount(); index++) pixels.add(positions.getCompoundTagAt(index).getLong("pos"));
            if (pixels.isEmpty()) return null;
            try {
                return new Television(tag.getLong("controller"), new UUID(tag.getLong("idMost"), tag.getLong("idLeast")),
                        new UUID(tag.getLong("ownerMost"), tag.getLong("ownerLeast")), pixels, tag.getInteger("width"),
                        tag.getInteger("height"), tag.getByteArray("mask"), ScreenFacing.valueOf(tag.getString("facing")),
                        tag.getInteger("minimumU"), tag.getInteger("minimumV"),
                        PresentationMode.valueOf(tag.getString("presentationMode")), tag.getString("sessionName"));
            } catch (IllegalArgumentException invalid) { return null; }
        }
    }

    public static final class Activation {
        private final boolean success;
        private final String message;
        private final Television television;
        Activation(boolean success, String message, Television television) { this.success = success; this.message = message; this.television = television; }
        public boolean success() { return success; }
        public String message() { return message; }
        public Television television() { return television; }
    }
}
