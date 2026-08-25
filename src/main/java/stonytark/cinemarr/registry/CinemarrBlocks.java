package stonytark.cinemarr.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.TvControllerBlock;

public final class CinemarrBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Cinemarr.MODID);
    private static BlockBehaviour.Properties electronic() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.METAL);
    }
    public static final DeferredBlock<ScreenPixelBlock> SCREEN_PIXEL =
            REGISTER.register("screen_pixel", () -> new ScreenPixelBlock(electronic().noOcclusion()));
    public static final DeferredBlock<TvControllerBlock> TV_CONTROLLER =
            REGISTER.register("tv_controller", () -> new TvControllerBlock(electronic()));
    public static final DeferredBlock<Block> TV_CASING =
            REGISTER.register("tv_casing", () -> new Block(electronic()));
    public static final DeferredBlock<Block> TV_SPEAKER =
            REGISTER.register("tv_speaker", () -> new Block(electronic()));
    private CinemarrBlocks() {}
}
