package stonytark.cinemarr.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.QuickTvBlock;
import stonytark.cinemarr.screen.TvControllerBlock;
import stonytark.cinemarr.core.screen.QuickTvPreset;

public final class CinemarrBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Cinemarr.MODID);
    private static BlockBehaviour.Properties electronic() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2, 6).sound(SoundType.METAL);
    }
    public static final DeferredBlock<ScreenPixelBlock> SCREEN_PIXEL = REGISTER.register(
            "screen_pixel", () -> new ScreenPixelBlock(electronic().noOcclusion()));
    public static final DeferredBlock<TvControllerBlock> TV_CONTROLLER = REGISTER.register(
            "tv_controller", () -> new TvControllerBlock(electronic()));
    public static final DeferredBlock<Block> TV_CASING = REGISTER.register("tv_casing", () -> new Block(electronic()));
    public static final DeferredBlock<Block> TV_SPEAKER = REGISTER.register("tv_speaker", () -> new Block(electronic()));
    public static final DeferredBlock<Block> REDSTONE_RECEIVER = REGISTER.register("redstone_receiver", () -> new Block(electronic()));
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_144P = quick("quick_tv_144p", QuickTvPreset.P144);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_240P = quick("quick_tv_240p", QuickTvPreset.P240);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_480P = quick("quick_tv_480p", QuickTvPreset.P480);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_720P = quick("quick_tv_720p", QuickTvPreset.P720);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_1080P = quick("quick_tv_1080p", QuickTvPreset.P1080);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_1440P = quick("quick_tv_1440p", QuickTvPreset.P1440);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_4K = quick("quick_tv_4k", QuickTvPreset.P4K);
    public static final DeferredBlock<QuickTvBlock> QUICK_TV_8K = quick("quick_tv_8k", QuickTvPreset.P8K);
    private static DeferredBlock<QuickTvBlock> quick(String id, QuickTvPreset preset) { return REGISTER.register(id, () -> new QuickTvBlock(electronic(), preset)); }
    public static ScreenPixelBlock screenPixel() { return SCREEN_PIXEL.get(); }
    public static Block redstoneReceiver() { return REDSTONE_RECEIVER.get(); }
    private CinemarrBlocks() {}
}
