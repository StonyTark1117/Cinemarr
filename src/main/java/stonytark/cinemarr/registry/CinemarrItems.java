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
    public static final DeferredItem<BlockItem> QUICK_TV_144P = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_144P);
    public static final DeferredItem<BlockItem> QUICK_TV_240P = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_240P);
    public static final DeferredItem<BlockItem> QUICK_TV_480P = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_480P);
    public static final DeferredItem<BlockItem> QUICK_TV_720P = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_720P);
    public static final DeferredItem<BlockItem> QUICK_TV_1080P = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_1080P);
    public static final DeferredItem<BlockItem> QUICK_TV_1440P = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_1440P);
    public static final DeferredItem<BlockItem> QUICK_TV_4K = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_4K);
    public static final DeferredItem<BlockItem> QUICK_TV_8K = REGISTER.registerSimpleBlockItem(CinemarrBlocks.QUICK_TV_8K);
    private CinemarrItems() {}
}
