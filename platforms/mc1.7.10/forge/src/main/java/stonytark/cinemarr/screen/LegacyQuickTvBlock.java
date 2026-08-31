package stonytark.cinemarr.screen;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;

/** Forge 1.7.10 quick-build television controller. */
public final class LegacyQuickTvBlock extends Block {
    private static final int BLOCKS_PER_TICK = 256;
    private static final java.util.Map<WorldServer, java.util.Map<Long, BuildJob>> JOBS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<WorldServer, java.util.Map<Long, BuildJob>>());
    private final QuickTvPreset preset;

    public LegacyQuickTvBlock(QuickTvPreset preset) {
        super(Material.iron);
        this.preset = preset;
        setBlockName("cinemarr.quick_tv_" + preset.id()); setBlockTextureName("cinemarr:quick_tv_" + preset.id());
        setHardness(2.0F); setResistance(6.0F); setCreativeTab(LegacyBlocks.TAB);
    }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        int quadrant = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        int facing = quadrant == 0 ? 3 : quadrant == 1 ? 4 : quadrant == 2 ? 2 : 5;
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);
        if (!world.isRemote && world instanceof WorldServer && placer instanceof EntityPlayer) {
            build((WorldServer) world, x, y, z, facing, (EntityPlayer) placer);
        }
    }

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                              int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote || !(world instanceof WorldServer)) return true;
        LegacyWorldScreens screens = LegacyWorldScreens.get((WorldServer) world);
        long controller = LegacyBlockPos.pack(x, y, z);
        if (screens.television(controller) == null && !build((WorldServer) world, x, y, z,
                world.getBlockMetadata(x, y, z), player)) return true;
        if (player instanceof EntityPlayerMP) LegacyNetwork.sendToPlayer((EntityPlayerMP) player,
                LegacyPacketTypes.OPEN_VIDEO_SCREEN, new LegacyPacketTypes.OpenVideoScreen(controller));
        return true;
    }

    private boolean build(WorldServer world, int x, int y, int z, int facing, EntityPlayer player) {
        String problem = preflight(world, x, y, z, facing, player);
        if (problem != null) { player.addChatMessage(new ChatComponentText(problem)); return false; }
        long controller = LegacyBlockPos.pack(x, y, z);
        java.util.Map<Long, BuildJob> jobs = jobs(world);
        if (jobs.containsKey(controller)) {
            player.addChatMessage(new ChatComponentText(preset.id() + " Quick TV construction is already in progress"));
            return true;
        }
        int frontX = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int frontZ = facing == 2 ? -1 : facing == 3 ? 1 : 0;
        int acrossX = facing == 2 ? 1 : facing == 3 ? -1 : 0;
        int acrossZ = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int anchorX = x + frontX - acrossX * (preset.physicalWidth() / 2);
        int anchorZ = z + frontZ - acrossZ * (preset.physicalWidth() / 2);
        java.util.List<Long> targets = new java.util.ArrayList<Long>(preset.physicalPixels());
        for (int py = 0; py < preset.physicalHeight(); py++) for (int px = 0; px < preset.physicalWidth(); px++) {
            targets.add(LegacyBlockPos.pack(anchorX + acrossX * px, y + py, anchorZ + acrossZ * px));
        }
        LegacyWorldScreens.get(world).beginQuickTvConstruction(controller, targets);
        jobs.put(controller, new BuildJob(player.getUniqueID(), player.getCommandSenderName(), facing, targets));
        if (ProtocolLimits.lifecycleProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance Quick TV lifecycle build started: controller={} preset={} pixels={}", controller, preset.id(), targets.size());
        boolean completed = advance(world, x, y, z);
        if (!completed && jobs(world).containsKey(controller)) {
            world.scheduleBlockUpdate(x, y, z, this, 1);
            player.addChatMessage(new ChatComponentText("Building " + preset.id() + " Quick TV in bounded 256-block batches"));
        }
        return completed || jobs(world).containsKey(controller);
    }

    @Override public void updateTick(World world, int x, int y, int z, java.util.Random random) {
        if (!(world instanceof WorldServer)) return;
        WorldServer server = (WorldServer) world;
        if (!advance(server, x, y, z) && jobs(server).containsKey(LegacyBlockPos.pack(x, y, z))) {
            server.scheduleBlockUpdate(x, y, z, this, 1);
        }
    }

    private boolean advance(WorldServer world, int x, int y, int z) {
        long controller = LegacyBlockPos.pack(x, y, z);
        BuildJob job = jobs(world).get(controller);
        if (job == null) return false;
        if (job.probePaused) return false;
        if (world.getBlock(x, y, z) != this) { rollback(world, controller, job); return false; }
        int end = Math.min(job.targets.size(), job.index + BLOCKS_PER_TICK);
        for (; job.index < end; job.index++) {
            long target = job.targets.get(job.index);
            int tx = LegacyBlockPos.x(target), ty = LegacyBlockPos.y(target), tz = LegacyBlockPos.z(target);
            boolean loaded = world.blockExists(tx, ty, tz);
            boolean air = loaded && world.isAirBlock(tx, ty, tz);
            if (!loaded || !air) {
                if (ProtocolLimits.lifecycleProbeEnabled()) Cinemarr.LOGGER.info(
                        "Acceptance Quick TV lifecycle rollback: target={},{},{} loaded={} block={} placed={}",
                        tx, ty, tz, loaded, loaded ? world.getBlock(tx, ty, tz).getUnlocalizedName() : "unloaded", job.placed.size());
                rollback(world, controller, job);
                message(world, job.ownerName, "Quick TV construction rolled back because its footprint changed or unloaded");
                return false;
            }
            world.setBlock(tx, ty, tz, LegacyBlocks.SCREEN_PIXEL, job.facing, 3);
            job.placed.add(target);
        }
        if (ProtocolLimits.lifecycleProbeEnabled() && !job.probeCheckpoint) {
            job.probeCheckpoint = true; job.probePaused = true;
            Cinemarr.LOGGER.info("Acceptance Quick TV lifecycle checkpoint: controller={} placed={} remaining={}",
                    controller, job.placed.size(), job.targets.size() - job.index);
            return false;
        }
        if (job.index < job.targets.size()) return false;
        jobs(world).remove(controller);
        LegacyWorldScreens screens = LegacyWorldScreens.get(world);
        LegacyWorldScreens.Activation activated = screens.activate(x, y, z, job.owner);
        if (!activated.success()) {
            screens.finishQuickTvConstruction(controller);
            rollbackPlaced(world, job);
            message(world, job.ownerName, activated.message());
            return false;
        }
        screens.updateRendition(controller, preset.renditionWidth(), preset.renditionHeight());
        screens.finishQuickTvConstruction(controller);
        Cinemarr.televisionActivated(world, screens.television(controller));
        message(world, job.ownerName, "Built " + preset.id() + " Quick TV: " + preset.physicalWidth() + "x"
                + preset.physicalHeight() + " screen, " + preset.renditionWidth() + "x" + preset.renditionHeight()
                + " rendition target");
        return true;
    }

    private static java.util.Map<Long, BuildJob> jobs(WorldServer world) {
        synchronized (JOBS) {
            java.util.Map<Long, BuildJob> values = JOBS.get(world);
            if (values == null) { values = new java.util.HashMap<Long, BuildJob>(); JOBS.put(world, values); }
            return values;
        }
    }
    private static void rollback(WorldServer world, long controller, BuildJob job) { jobs(world).remove(controller); LegacyWorldScreens.get(world).finishQuickTvConstruction(controller); rollbackPlaced(world, job); }
    private static void rollbackPlaced(WorldServer world, BuildJob job) {
        for (int index = job.placed.size() - 1; index >= 0; index--) {
            long target = job.placed.get(index); int x = LegacyBlockPos.x(target), y = LegacyBlockPos.y(target), z = LegacyBlockPos.z(target);
            if (world.getBlock(x, y, z) == LegacyBlocks.SCREEN_PIXEL) world.setBlockToAir(x, y, z);
        }
    }
    private static void message(WorldServer world, String playerName, String text) {
        EntityPlayer player = world.getPlayerEntityByName(playerName);
        if (player != null) player.addChatMessage(new ChatComponentText(text));
    }
    private static final class BuildJob {
        final java.util.UUID owner; final String ownerName; final int facing;
        final java.util.List<Long> targets; final java.util.List<Long> placed = new java.util.ArrayList<Long>(); int index; boolean probeCheckpoint, probePaused;
        BuildJob(java.util.UUID owner, String ownerName, int facing, java.util.List<Long> targets) {
            this.owner = owner; this.ownerName = ownerName; this.facing = facing; this.targets = targets;
        }
    }

    private String preflight(WorldServer world, int x, int y, int z, int facing, EntityPlayer player) {
        if (!CinemarrSettings.quickTvPresetEnabled(preset)) return preset.id() + " Quick TV kits are disabled by the server";
        LegacyWorldScreens screens=LegacyWorldScreens.get(world);
        long controller=LegacyBlockPos.pack(x,y,z);
        if(screens.television(controller)==null&&TelevisionLifecycle.count(player.getUniqueID())>=CinemarrSettings.maximumScreensPerOwner())return "Maximum screens per owner reached";
        if (preset.physicalPixels() < CinemarrSettings.minimumScreenPixels()
                || preset.physicalPixels() > CinemarrSettings.maximumScreenPixels()
                || preset.physicalWidth() > CinemarrSettings.maximumScreenDimension()
                || preset.physicalHeight() > CinemarrSettings.maximumScreenDimension()) {
            return preset.id() + " Quick TV exceeds this server's screen construction limits";
        }
        int frontX = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int frontZ = facing == 2 ? -1 : facing == 3 ? 1 : 0;
        int acrossX = facing == 2 ? 1 : facing == 3 ? -1 : 0;
        int acrossZ = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int anchorX = x + frontX - acrossX * (preset.physicalWidth() / 2);
        int anchorZ = z + frontZ - acrossZ * (preset.physicalWidth() / 2);
        java.util.Set<Long> footprint=new java.util.HashSet<Long>();
        for (int py = 0; py < preset.physicalHeight(); py++) for (int px = 0; px < preset.physicalWidth(); px++) {
            int targetX = anchorX + acrossX * px, targetY = y + py, targetZ = anchorZ + acrossZ * px;
            footprint.add(LegacyBlockPos.pack(targetX,targetY,targetZ));
            if (targetY < 0 || targetY >= 256 || !world.blockExists(targetX, targetY, targetZ)) {
                return "Load the complete " + preset.id() + " Quick TV footprint before building";
            }
            Block existing = world.getBlock(targetX, targetY, targetZ);
            if (!world.isAirBlock(targetX, targetY, targetZ)) {
                return "The " + preset.id() + " Quick TV footprint is obstructed at " + targetX + "," + targetY + "," + targetZ;
            }
        }
        if(screens.overlaps(controller,footprint))return "Quick TV pixels already belong to another TV";
        return null;
    }

    @Override public void breakBlock(World world, int x, int y, int z, Block replacement, int metadata) {
        if (!world.isRemote && world instanceof WorldServer) {
            BuildJob job=jobs((WorldServer)world).get(LegacyBlockPos.pack(x,y,z));if(job!=null)rollback((WorldServer)world,LegacyBlockPos.pack(x,y,z),job);
            LegacyWorldScreens.Television removed=LegacyWorldScreens.get((WorldServer) world).removeController(x, y, z);
            if(removed!=null)for(Long packed:removed.pixels())if(world.getBlock(LegacyBlockPos.x(packed),LegacyBlockPos.y(packed),LegacyBlockPos.z(packed))==LegacyBlocks.SCREEN_PIXEL)world.setBlockToAir(LegacyBlockPos.x(packed),LegacyBlockPos.y(packed),LegacyBlockPos.z(packed));
        }
        super.breakBlock(world, x, y, z, replacement, metadata);
    }
}
