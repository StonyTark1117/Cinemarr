package stonytark.cinemarr.screen;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Forge 1.7.10 block registration for the shared television vocabulary. */
public final class LegacyBlocks {
    public static final Block SCREEN_PIXEL = new LegacyScreenPixelBlock();
    public static final Block TV_CONTROLLER = new LegacyTvControllerBlock();
    public static final Block TV_CASING = decorative("tv_casing", "minecraft:obsidian");
    public static final Block TV_SPEAKER = decorative("tv_speaker", "minecraft:noteblock");

    public static void register() {
        GameRegistry.registerBlock(SCREEN_PIXEL, "screen_pixel");
        GameRegistry.registerBlock(TV_CONTROLLER, "tv_controller");
        GameRegistry.registerBlock(TV_CASING, "tv_casing");
        GameRegistry.registerBlock(TV_SPEAKER, "tv_speaker");
    }

    public static void registerRecipes() {
        GameRegistry.addRecipe(new ItemStack(SCREEN_PIXEL, 4), "igi", "grg", "igi",
                'i', Items.iron_ingot, 'g', Blocks.glass_pane, 'r', Items.redstone);
        GameRegistry.addRecipe(new ItemStack(TV_CONTROLLER), "iri", "rdr", "iri",
                'i', Items.iron_ingot, 'r', Items.redstone, 'd', Items.diamond);
        GameRegistry.addRecipe(new ItemStack(TV_CASING, 8), "iii", "i i", "iii", 'i', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(TV_SPEAKER, 2), "ini", "nrn", "ini",
                'i', Items.iron_ingot, 'n', Blocks.noteblock, 'r', Items.redstone);
    }

    private static Block decorative(final String name, String texture) {
        return new Block(Material.iron) {{ setBlockName("cinemarr." + name); setBlockTextureName(texture);
            setHardness(2.0F); setResistance(6.0F); setCreativeTab(CreativeTabs.tabDecorations); }};
    }

    private LegacyBlocks() {}
}
