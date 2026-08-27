package stonytark.cinemarr.screen;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;

/** Activates or refreshes an adjacent recorded screen silhouette. */
public final class LegacyTvControllerBlock extends Block {
    public LegacyTvControllerBlock() {
        super(Material.iron);
        setBlockName("cinemarr.tv_controller"); setBlockTextureName("minecraft:iron_block");
        setHardness(2.0F); setResistance(6.0F); setCreativeTab(CreativeTabs.tabDecorations);
    }

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                              int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote || !(world instanceof WorldServer)) return true;
        LegacyWorldScreens.Activation result = LegacyWorldScreens.get((WorldServer) world).activate(x, y, z, player.getUniqueID());
        player.addChatMessage(new ChatComponentText(result.message()));
        if (result.success() && player instanceof EntityPlayerMP) LegacyNetwork.sendToPlayer((EntityPlayerMP) player,
                LegacyPacketTypes.OPEN_VIDEO_SCREEN, new LegacyPacketTypes.OpenVideoScreen(LegacyBlockPos.pack(x, y, z)));
        return true;
    }

    @Override public void breakBlock(World world, int x, int y, int z, Block replacement, int metadata) {
        if (!world.isRemote && world instanceof WorldServer) LegacyWorldScreens.get((WorldServer) world).removeController(x, y, z);
        super.breakBlock(world, x, y, z, replacement, metadata);
    }
}
