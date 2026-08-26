package stonytark.cinemarr.registry;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Cinemarr.MODID);
    public static final DeferredItem<BlockItem> SCREEN_PIXEL = REGISTER.registerSimpleBlockItem(CinemarrBlocks.SCREEN_PIXEL);
    public static final DeferredItem<BlockItem> TV_CONTROLLER = REGISTER.registerSimpleBlockItem(CinemarrBlocks.TV_CONTROLLER);
    public static final DeferredItem<BlockItem> TV_CASING = REGISTER.registerSimpleBlockItem(CinemarrBlocks.TV_CASING);
    public static final DeferredItem<BlockItem> TV_SPEAKER = REGISTER.registerSimpleBlockItem(CinemarrBlocks.TV_SPEAKER);
    private CinemarrItems() {}
}
