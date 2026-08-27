package stonytark.cinemarr.screen;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import stonytark.cinemarr.core.screen.QuickTvPreset;

/** Forge 1.7.10 block registration for the shared television vocabulary. */
public final class LegacyBlocks {
    public static final Block SCREEN_PIXEL = new LegacyScreenPixelBlock();
    public static final Block TV_CONTROLLER = new LegacyTvControllerBlock();
    public static final Block TV_CASING = decorative("tv_casing", "minecraft:obsidian");
    public static final Block TV_SPEAKER = decorative("tv_speaker", "minecraft:noteblock");
    public static final Block QUICK_TV_144P = quick(QuickTvPreset.P144, "minecraft:coal_block");
    public static final Block QUICK_TV_240P = quick(QuickTvPreset.P240, "minecraft:stone");
    public static final Block QUICK_TV_480P = quick(QuickTvPreset.P480, "minecraft:iron_block");
    public static final Block QUICK_TV_720P = quick(QuickTvPreset.P720, "minecraft:lapis_block");
    public static final Block QUICK_TV_1080P = quick(QuickTvPreset.P1080, "minecraft:redstone_block");
    public static final Block QUICK_TV_1440P = quick(QuickTvPreset.P1440, "minecraft:gold_block");
    public static final Block QUICK_TV_4K = quick(QuickTvPreset.P4K, "minecraft:emerald_block");
    public static final Block QUICK_TV_8K = quick(QuickTvPreset.P8K, "minecraft:diamond_block");

    public static void register() {
        GameRegistry.registerBlock(SCREEN_PIXEL, "screen_pixel");
        GameRegistry.registerBlock(TV_CONTROLLER, "tv_controller");
        GameRegistry.registerBlock(TV_CASING, "tv_casing");
        GameRegistry.registerBlock(TV_SPEAKER, "tv_speaker");
        GameRegistry.registerBlock(QUICK_TV_144P, "quick_tv_144p");
        GameRegistry.registerBlock(QUICK_TV_240P, "quick_tv_240p");
        GameRegistry.registerBlock(QUICK_TV_480P, "quick_tv_480p");
        GameRegistry.registerBlock(QUICK_TV_720P, "quick_tv_720p");
        GameRegistry.registerBlock(QUICK_TV_1080P, "quick_tv_1080p");
        GameRegistry.registerBlock(QUICK_TV_1440P, "quick_tv_1440p");
        GameRegistry.registerBlock(QUICK_TV_4K, "quick_tv_4k");
        GameRegistry.registerBlock(QUICK_TV_8K, "quick_tv_8k");
    }

    public static void registerRecipes() {
        GameRegistry.addRecipe(new ItemStack(SCREEN_PIXEL, 4), "igi", "grg", "igi",
                'i', Items.iron_ingot, 'g', Blocks.glass_pane, 'r', Items.redstone);
        GameRegistry.addRecipe(new ItemStack(TV_CONTROLLER), "iri", "rdr", "iri",
                'i', Items.iron_ingot, 'r', Items.redstone, 'd', Items.diamond);
        GameRegistry.addRecipe(new ItemStack(TV_CASING, 8), "iii", "i i", "iii", 'i', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(TV_SPEAKER, 2), "ini", "nrn", "ini",
                'i', Items.iron_ingot, 'n', Blocks.noteblock, 'r', Items.redstone);
        GameRegistry.addRecipe(new ItemStack(QUICK_TV_144P), "sgs", "gcg", "srs",
                's', SCREEN_PIXEL, 'g', Blocks.glass, 'c', TV_CONTROLLER, 'r', Blocks.redstone_block);
        upgrade(QUICK_TV_240P, QUICK_TV_144P, Blocks.iron_block);
        upgrade(QUICK_TV_480P, QUICK_TV_240P, Blocks.lapis_block);
        upgrade(QUICK_TV_720P, QUICK_TV_480P, Blocks.redstone_block);
        upgrade(QUICK_TV_1080P, QUICK_TV_720P, Blocks.gold_block);
        upgrade(QUICK_TV_1440P, QUICK_TV_1080P, Blocks.obsidian);
        upgrade(QUICK_TV_4K, QUICK_TV_1440P, Blocks.emerald_block);
        upgrade(QUICK_TV_8K, QUICK_TV_4K, Blocks.diamond_block);
    }

    private static Block decorative(final String name, String texture) {
        return new Block(Material.iron) {{ setBlockName("cinemarr." + name); setBlockTextureName(texture);
            setHardness(2.0F); setResistance(6.0F); setCreativeTab(CreativeTabs.tabDecorations); }};
    }
    private static Block quick(QuickTvPreset preset, String texture) { return new LegacyQuickTvBlock(preset, texture); }
    private static void upgrade(Block output, Block previous, Block material) {
        GameRegistry.addRecipe(new ItemStack(output), "mmm", "mkm", "mmm", 'm', material, 'k', previous);
    }

    private LegacyBlocks() {}
}
