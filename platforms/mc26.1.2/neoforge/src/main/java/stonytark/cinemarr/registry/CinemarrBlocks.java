package stonytark.cinemarr.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import stonytark.cinemarr.screen.TvControllerBlock;

import java.util.function.Function;

public final class CinemarrBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Cinemarr.MODID);
    public static final DeferredBlock<ScreenPixelBlock> SCREEN_PIXEL = register("screen_pixel",
            properties -> new ScreenPixelBlock(properties.noOcclusion()));
    public static final DeferredBlock<TvControllerBlock> TV_CONTROLLER = register("tv_controller", TvControllerBlock::new);
    public static final DeferredBlock<Block> TV_CASING = register("tv_casing", Block::new);
    public static final DeferredBlock<Block> TV_SPEAKER = register("tv_speaker", Block::new);

    private static <T extends Block> DeferredBlock<T> register(String name, Function<BlockBehaviour.Properties, T> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(Cinemarr.MODID, name));
        return REGISTER.register(name, () -> factory.apply(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.METAL)));
    }
    private CinemarrBlocks() {}
}
