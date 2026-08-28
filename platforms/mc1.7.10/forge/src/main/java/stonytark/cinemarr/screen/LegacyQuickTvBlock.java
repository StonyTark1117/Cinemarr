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
import stonytark.cinemarr.core.screen.QuickTvPreset;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;

/** Forge 1.7.10 quick-build television controller. */
public final class LegacyQuickTvBlock extends Block {
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
        String problem = preflight(world, x, y, z, facing);
        if (problem != null) { player.addChatMessage(new ChatComponentText(problem)); return false; }
        int frontX = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int frontZ = facing == 2 ? -1 : facing == 3 ? 1 : 0;
        int acrossX = facing == 2 ? 1 : facing == 3 ? -1 : 0;
        int acrossZ = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int anchorX = x + frontX - acrossX * (preset.physicalWidth() / 2);
        int anchorZ = z + frontZ - acrossZ * (preset.physicalWidth() / 2);
        for (int py = 0; py < preset.physicalHeight(); py++) for (int px = 0; px < preset.physicalWidth(); px++) {
            world.setBlock(anchorX + acrossX * px, y + py, anchorZ + acrossZ * px, LegacyBlocks.SCREEN_PIXEL, facing, 3);
        }
        LegacyWorldScreens screens = LegacyWorldScreens.get(world);
        LegacyWorldScreens.Activation activated = screens.activate(x, y, z, player.getUniqueID());
        if (!activated.success()) { player.addChatMessage(new ChatComponentText(activated.message())); return false; }
        screens.updateRendition(LegacyBlockPos.pack(x, y, z), preset.renditionWidth(), preset.renditionHeight());
        Cinemarr.televisionActivated(world, screens.television(LegacyBlockPos.pack(x, y, z)));
        player.addChatMessage(new ChatComponentText("Built " + preset.id() + " Quick TV: "
                + preset.physicalWidth() + "x" + preset.physicalHeight() + " dense screen, "
                + preset.renditionWidth() + "x" + preset.renditionHeight() + " rendition target"));
        return true;
    }

    private String preflight(WorldServer world, int x, int y, int z, int facing) {
        if (!CinemarrSettings.quickTvPresetEnabled(preset)) return preset.id() + " Quick TV kits are disabled by the server";
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
        for (int py = 0; py < preset.physicalHeight(); py++) for (int px = 0; px < preset.physicalWidth(); px++) {
            int targetX = anchorX + acrossX * px, targetY = y + py, targetZ = anchorZ + acrossZ * px;
            if (targetY < 0 || targetY >= 256 || !world.blockExists(targetX, targetY, targetZ)) {
                return "Load the complete " + preset.id() + " Quick TV footprint before building";
            }
            Block existing = world.getBlock(targetX, targetY, targetZ);
            if (!world.isAirBlock(targetX, targetY, targetZ) && existing != LegacyBlocks.SCREEN_PIXEL) {
                return "The " + preset.id() + " Quick TV footprint is obstructed at " + targetX + "," + targetY + "," + targetZ;
            }
        }
        return null;
    }

    @Override public void breakBlock(World world, int x, int y, int z, Block replacement, int metadata) {
        if (!world.isRemote && world instanceof WorldServer) LegacyWorldScreens.get((WorldServer) world).removeController(x, y, z);
        super.breakBlock(world, x, y, z, replacement, metadata);
    }
}
