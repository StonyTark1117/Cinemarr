package stonytark.cinemarr.screen;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.Cinemarr;
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
    public static final int SCHEMA_VERSION = 3;
    private final Map<Long, ScreenFacing> pixels = new HashMap<Long, ScreenFacing>();
    private final Map<Long, Television> televisions = new HashMap<Long, Television>();
    private final Map<Long, Set<Long>> quickTvConstructions = new HashMap<Long, Set<Long>>();
    private final transient String detachedDimension = "detached:" + UUID.randomUUID();
    private transient WorldServer world;
    private transient boolean registrationsReconciled;
    private transient boolean constructionsRecovered;
    private transient boolean recoveryProbeLogged;

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
        value.world = world;
        if(!value.registrationsReconciled)value.reconcileRegistrations();
        if(!value.constructionsRecovered)value.recoverConstructions();
        return value;
    }

    public void beginQuickTvConstruction(long controller, java.util.Collection<Long> targets) {
        quickTvConstructions.put(controller, new HashSet<Long>(targets)); markDirty();
    }
    public void finishQuickTvConstruction(long controller) { if (quickTvConstructions.remove(controller) != null) markDirty(); }
    private void recoverConstructions() {
        if(world==null||quickTvConstructions.isEmpty()){constructionsRecovered=quickTvConstructions.isEmpty();return;}constructionsRecovered=true;boolean changed=false;int recovered=0;
        for(java.util.Iterator<Map.Entry<Long,Set<Long>>> builds=quickTvConstructions.entrySet().iterator();builds.hasNext();){Set<Long> footprint=builds.next().getValue();for(java.util.Iterator<Long> positions=footprint.iterator();positions.hasNext();){long packed=positions.next();int x=LegacyBlockPos.x(packed),y=LegacyBlockPos.y(packed),z=LegacyBlockPos.z(packed);if(!world.blockExists(x,y,z))continue;if(world.getBlock(x,y,z)==LegacyBlocks.SCREEN_PIXEL)world.setBlockToAir(x,y,z);positions.remove();changed=true;recovered++;}if(footprint.isEmpty())builds.remove();}
        constructionsRecovered=quickTvConstructions.isEmpty();if(changed)markDirty();if(ProtocolLimits.lifecycleProbeEnabled()&&(changed||!recoveryProbeLogged)){int remaining=0;for(Set<Long> footprint:quickTvConstructions.values())remaining+=footprint.size();Cinemarr.LOGGER.info("Acceptance Quick TV lifecycle recovery: removed={} remaining={} complete={}",recovered,remaining,quickTvConstructions.isEmpty());recoveryProbeLogged=true;}
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
        Television removed = televisions.get(LegacyBlockPos.pack(x, y, z));
        if (removed != null) {
            TelevisionLifecycle.unregister(removed.id, removed.sessionName);
            if (television(removed.id) != null) removeLocal(removed.id);
        }
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
                    CinemarrSettings.maximumScreenPixels(), CinemarrSettings.maximumScreenDimension()),
                    CinemarrSettings.allowIrregularScreens());
            Television existing = televisions.get(controller);
            for (Map.Entry<Long, Television> entry : televisions.entrySet()) {
                if (entry.getKey().longValue() != controller && intersects(entry.getValue().pixels, connected)) {
                    return new Activation(false, "Screen pixels already belong to another TV", null);
                }
            }
            UUID televisionId = existing == null ? UUID.randomUUID() : existing.id;
            UUID televisionOwner = existing == null ? owner : existing.owner;
            Television television = new Television(controller, televisionId,
                    televisionOwner, connected, geometry.width(), geometry.height(),
                    geometry.visibilityMask().toByteArray(), geometry.facing(), geometry.minimumU(), geometry.minimumV(),
                    existing == null ? PresentationMode.FIT : existing.presentationMode,
                    existing == null ? "" : existing.sessionName, geometry.width(), geometry.height());
            if (TelevisionLifecycle.overlaps(dimensionKey(), connected, televisionId)) {
                return new Activation(false, "Screen pixels already belong to another TV", null);
            }
            if (!TelevisionLifecycle.register(registration(television), CinemarrSettings.maximumScreensPerOwner())) {
                return new Activation(false, existing == null ? "Maximum screens per owner reached"
                        : "TV registration conflicts with another screen", null);
            }
            televisions.put(controller, television);
            markDirty();
            return new Activation(true, "Activated " + geometry.width() + "x" + geometry.height()
                    + " TV with " + geometry.pixelCount() + " visible pixels", television);
        } catch (IllegalArgumentException invalid) {
            return new Activation(false, invalid.getMessage(), null);
        }
    }

    public Television television(long controller) { return televisions.get(controller); }
    public Television television(UUID id) { for(Television value:televisions.values())if(value.id.equals(id))return value;return null; }
    public Television removeTelevision(UUID id) { Television value=television(id);return value==null?null:removeController(LegacyBlockPos.x(value.controllerPos),LegacyBlockPos.y(value.controllerPos),LegacyBlockPos.z(value.controllerPos)); }
    public List<Television> televisions() { return Collections.unmodifiableList(new ArrayList<Television>(televisions.values())); }
    public int pruneInvalid() {
        int removed = 0;
        for (Television television : new ArrayList<Television>(televisions.values())) {
            LiveValidation validation = liveValidation(television);
            if (validation == LiveValidation.INVALID) {
                if (TelevisionLifecycle.unregister(television.id, television.sessionName)) removed++;
                else { removeLocal(television.id); removed++; }
            } else TelevisionLifecycle.validation(television.id, validation == LiveValidation.LOADED
                    ? TelevisionLifecycle.Validation.LOADED : TelevisionLifecycle.Validation.SAVED);
        }
        return removed;
    }
    public boolean overlaps(long controller,Set<Long> candidates){for(Map.Entry<Long,Television> entry:televisions.entrySet())if(entry.getKey().longValue()!=controller&&intersects(entry.getValue().pixels,candidates))return true;return false;}
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
        if (value != null) { value.sessionName = name == null ? "" : name.trim(); TelevisionLifecycle.session(value.id, value.sessionName); markDirty(); }
    }
    public void updateRendition(long controller, int width, int height) {
        Television value = televisions.get(controller);
        if (value != null && width > 0 && height > 0) { value.renditionWidth = width; value.renditionHeight = height; markDirty(); }
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
        for (Long controller : invalid) removeController(LegacyBlockPos.x(controller), LegacyBlockPos.y(controller), LegacyBlockPos.z(controller));
    }

    private void reconcileRegistrations() {
        registrationsReconciled=true;
        List<Long> invalid = new ArrayList<Long>();
        Set<Long> occupied = new HashSet<Long>();
        for (Map.Entry<Long, Television> entry : televisions.entrySet()) {
            Television television = entry.getValue();
            if (!valid(television) || intersects(occupied, television.pixels)
                    || !TelevisionLifecycle.restore(registration(television))) invalid.add(entry.getKey());
            else occupied.addAll(television.pixels);
        }
        for (Long controller : invalid) removeLocalAt(controller);
        if (!invalid.isEmpty()) markDirty();
    }

    private boolean valid(Television television) {
        List<ScreenPixel> values = new ArrayList<ScreenPixel>();
        for (Long packed : television.pixels) {
            ScreenFacing facing = pixels.get(packed);
            if (facing == null) return false;
            values.add(new ScreenPixel(LegacyBlockPos.x(packed), LegacyBlockPos.y(packed), LegacyBlockPos.z(packed), facing));
        }
        try {
            ScreenGeometry geometry = ScreenTopology.analyze(values, new ScreenLimits(CinemarrSettings.minimumScreenPixels(),
                    CinemarrSettings.maximumScreenPixels(), CinemarrSettings.maximumScreenDimension()),
                    CinemarrSettings.allowIrregularScreens());
            return geometry.width() == television.width && geometry.height() == television.height;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private LiveValidation liveValidation(Television television) {
        if (world == null) return LiveValidation.UNLOADED;
        int controllerX = LegacyBlockPos.x(television.controllerPos), controllerY = LegacyBlockPos.y(television.controllerPos),
                controllerZ = LegacyBlockPos.z(television.controllerPos);
        if (!world.blockExists(controllerX, controllerY, controllerZ)) return LiveValidation.UNLOADED;
        for (Long packed : television.pixels) if (!world.blockExists(LegacyBlockPos.x(packed), LegacyBlockPos.y(packed), LegacyBlockPos.z(packed))) return LiveValidation.UNLOADED;
        net.minecraft.block.Block controller = world.getBlock(controllerX, controllerY, controllerZ);
        if (!(controller instanceof LegacyTvControllerBlock) && !(controller instanceof LegacyQuickTvBlock)) return LiveValidation.INVALID;
        for (Long packed : television.pixels) {
            int x = LegacyBlockPos.x(packed), y = LegacyBlockPos.y(packed), z = LegacyBlockPos.z(packed);
            if (!(world.getBlock(x, y, z) instanceof LegacyScreenPixelBlock)
                    || facing(world.getBlockMetadata(x, y, z)) != television.facing) return LiveValidation.INVALID;
        }
        return valid(television) ? LiveValidation.LOADED : LiveValidation.INVALID;
    }

    private TelevisionLifecycle.Registration registration(final Television television) {
        final UUID id = television.id;
        return new TelevisionLifecycle.Registration(id, television.owner, dimensionKey(), television.controllerPos,
                LegacyBlockPos.x(television.controllerPos), LegacyBlockPos.y(television.controllerPos),
                LegacyBlockPos.z(television.controllerPos), television.pixels, television.sessionName,
                TelevisionLifecycle.Validation.SAVED, new TelevisionLifecycle.Removal() {
                    @Override public void remove() { removeLocal(id); }
                });
    }
    private String dimensionKey() { return world == null ? detachedDimension : "dimension:" + world.provider.dimensionId; }
    private Television removeLocal(UUID id) { Television value = television(id); if (value != null) { televisions.remove(value.controllerPos); markDirty(); } return value; }
    private Television removeLocalAt(long controller) { Television value = televisions.remove(controller); if (value != null) markDirty(); return value; }
    private static ScreenFacing facing(int side) { switch (side) { case 0:return ScreenFacing.DOWN;case 1:return ScreenFacing.UP;case 3:return ScreenFacing.SOUTH;case 4:return ScreenFacing.WEST;case 5:return ScreenFacing.EAST;default:return ScreenFacing.NORTH; } }
    private enum LiveValidation { LOADED, UNLOADED, INVALID }

    private static boolean intersects(Set<Long> first, Set<Long> second) {
        for (Long value : first) if (second.contains(value)) return true;
        return false;
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
        NBTTagList constructionTags = tag.getTagList("quickTvConstructions", 10);
        for (int index = 0; index < constructionTags.tagCount(); index++) {
            NBTTagCompound value = constructionTags.getCompoundTagAt(index); Set<Long> positions = new HashSet<Long>();
            NBTTagList pixels = value.getTagList("pixels", 10);
            for (int pixelIndex = 0; pixelIndex < pixels.tagCount(); pixelIndex++) positions.add(pixels.getCompoundTagAt(pixelIndex).getLong("pos"));
            if (!positions.isEmpty()) quickTvConstructions.put(value.getLong("controller"), positions);
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
        NBTTagList constructionTags = new NBTTagList();
        for (Map.Entry<Long, Set<Long>> entry : quickTvConstructions.entrySet()) {
            NBTTagCompound value = new NBTTagCompound(); value.setLong("controller", entry.getKey()); NBTTagList positions = new NBTTagList();
            for (Long position : entry.getValue()) { NBTTagCompound pixel = new NBTTagCompound(); pixel.setLong("pos", position); positions.appendTag(pixel); }
            value.setTag("pixels", positions); constructionTags.appendTag(value);
        }
        tag.setTag("quickTvConstructions", constructionTags);
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
        private int renditionWidth, renditionHeight;

        Television(long controllerPos, UUID id, UUID owner, Set<Long> pixels, int width, int height, byte[] mask,
                   ScreenFacing facing, int minimumU, int minimumV, PresentationMode mode, String sessionName,
                   int renditionWidth, int renditionHeight) {
            this.controllerPos = controllerPos; this.id = id; this.owner = owner; this.pixels = new HashSet<Long>(pixels);
            this.width = width; this.height = height; this.mask = mask == null ? new byte[0] : mask.clone();
            this.facing = facing; this.minimumU = minimumU; this.minimumV = minimumV;
            this.presentationMode = mode; this.sessionName = sessionName == null ? "" : sessionName;
            this.renditionWidth = renditionWidth > 0 ? renditionWidth : width;
            this.renditionHeight = renditionHeight > 0 ? renditionHeight : height;
        }

        public long controllerPos() { return controllerPos; }
        public UUID id() { return id; }
        public UUID owner() { return owner; }
        public Set<Long> pixels() { return Collections.unmodifiableSet(pixels); }
        public int width() { return width; }
        public int height() { return height; }
        public int renditionWidth() { return renditionWidth; }
        public int renditionHeight() { return renditionHeight; }
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
            tag.setInteger("renditionWidth", renditionWidth); tag.setInteger("renditionHeight", renditionHeight);
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
                        PresentationMode.valueOf(tag.getString("presentationMode")), tag.getString("sessionName"),
                        tag.hasKey("renditionWidth") ? tag.getInteger("renditionWidth") : tag.getInteger("width"),
                        tag.hasKey("renditionHeight") ? tag.getInteger("renditionHeight") : tag.getInteger("height"));
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
