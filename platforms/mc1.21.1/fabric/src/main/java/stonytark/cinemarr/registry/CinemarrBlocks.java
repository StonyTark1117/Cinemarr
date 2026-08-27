package stonytark.cinemarr.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.QuickTvBlock;
import stonytark.cinemarr.screen.TvControllerBlock;
import stonytark.cinemarr.core.screen.QuickTvPreset;

public final class CinemarrBlocks {
    private static BlockBehaviour.Properties electronic() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.METAL);
    }

    public static final ScreenPixelBlock SCREEN_PIXEL = new ScreenPixelBlock(electronic().noOcclusion());
    public static final TvControllerBlock TV_CONTROLLER = new TvControllerBlock(electronic());
    public static final Block TV_CASING = new Block(electronic());
    public static final Block TV_SPEAKER = new Block(electronic());
    public static final Block REDSTONE_RECEIVER = new Block(electronic());
    public static final QuickTvBlock QUICK_TV_144P = quick(QuickTvPreset.P144);
    public static final QuickTvBlock QUICK_TV_240P = quick(QuickTvPreset.P240);
    public static final QuickTvBlock QUICK_TV_480P = quick(QuickTvPreset.P480);
    public static final QuickTvBlock QUICK_TV_720P = quick(QuickTvPreset.P720);
    public static final QuickTvBlock QUICK_TV_1080P = quick(QuickTvPreset.P1080);
    public static final QuickTvBlock QUICK_TV_1440P = quick(QuickTvPreset.P1440);
    public static final QuickTvBlock QUICK_TV_4K = quick(QuickTvPreset.P4K);
    public static final QuickTvBlock QUICK_TV_8K = quick(QuickTvPreset.P8K);

    public static void register() {
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id("screen_pixel"), SCREEN_PIXEL);
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id("tv_controller"), TV_CONTROLLER);
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id("tv_casing"), TV_CASING);
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id("tv_speaker"), TV_SPEAKER);
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id("redstone_receiver"), REDSTONE_RECEIVER);
        registerQuick("144p", QUICK_TV_144P); registerQuick("240p", QUICK_TV_240P);
        registerQuick("480p", QUICK_TV_480P); registerQuick("720p", QUICK_TV_720P);
        registerQuick("1080p", QUICK_TV_1080P); registerQuick("1440p", QUICK_TV_1440P);
        registerQuick("4k", QUICK_TV_4K); registerQuick("8k", QUICK_TV_8K);
    }

    private static QuickTvBlock quick(QuickTvPreset preset) { return new QuickTvBlock(electronic(), preset); }
    private static void registerQuick(String id, QuickTvBlock block) {
        net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, id("quick_tv_" + id), block);
    }
    public static ScreenPixelBlock screenPixel() { return SCREEN_PIXEL; }
    public static Block redstoneReceiver() { return REDSTONE_RECEIVER; }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, path); }
    private CinemarrBlocks() {}
}
