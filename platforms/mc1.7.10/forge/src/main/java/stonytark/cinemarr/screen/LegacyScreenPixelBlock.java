package stonytark.cinemarr.screen;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.world.World;
import stonytark.cinemarr.core.screen.ScreenFacing;

/** Ordinary metadata-facing screen pixel; it never ticks and has no tile entity. */
public final class LegacyScreenPixelBlock extends Block {
    public LegacyScreenPixelBlock() {
        super(Material.iron);
        setBlockName("cinemarr.screen_pixel"); setBlockTextureName("minecraft:coal_block");
        setHardness(2.0F); setResistance(6.0F); setCreativeTab(CreativeTabs.tabDecorations);
    }

    @Override public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata) {
        return side >= 0 && side < 6 ? side : 2;
    }

    @Override public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote && world instanceof net.minecraft.world.WorldServer) {
            LegacyWorldScreens.get((net.minecraft.world.WorldServer) world).putPixel(x, y, z, facing(world.getBlockMetadata(x, y, z)));
        }
    }

    @Override public void breakBlock(World world, int x, int y, int z, Block replacement, int metadata) {
        if (!world.isRemote && world instanceof net.minecraft.world.WorldServer) {
            LegacyWorldScreens.get((net.minecraft.world.WorldServer) world).removePixel(x, y, z);
        }
        super.breakBlock(world, x, y, z, replacement, metadata);
    }

    private static ScreenFacing facing(int side) {
        switch (side) {
            case 0: return ScreenFacing.DOWN; case 1: return ScreenFacing.UP; case 3: return ScreenFacing.SOUTH;
            case 4: return ScreenFacing.WEST; case 5: return ScreenFacing.EAST; default: return ScreenFacing.NORTH;
        }
    }
}
