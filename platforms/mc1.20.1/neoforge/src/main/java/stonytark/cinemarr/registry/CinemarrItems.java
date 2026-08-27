package stonytark.cinemarr.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, Cinemarr.MODID);
    public static final RegistryObject<BlockItem> SCREEN_PIXEL = block("screen_pixel", CinemarrBlocks.SCREEN_PIXEL);
    public static final RegistryObject<BlockItem> TV_CONTROLLER = block("tv_controller", CinemarrBlocks.TV_CONTROLLER);
    public static final RegistryObject<BlockItem> TV_CASING = block("tv_casing", CinemarrBlocks.TV_CASING);
    public static final RegistryObject<BlockItem> TV_SPEAKER = block("tv_speaker", CinemarrBlocks.TV_SPEAKER);
    public static final RegistryObject<BlockItem> REDSTONE_RECEIVER = block("redstone_receiver", CinemarrBlocks.REDSTONE_RECEIVER);
    public static final RegistryObject<Item> TV_REMOTE = REGISTER.register("tv_remote", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockItem> QUICK_TV_144P = block("quick_tv_144p", CinemarrBlocks.QUICK_TV_144P);
    public static final RegistryObject<BlockItem> QUICK_TV_240P = block("quick_tv_240p", CinemarrBlocks.QUICK_TV_240P);
    public static final RegistryObject<BlockItem> QUICK_TV_480P = block("quick_tv_480p", CinemarrBlocks.QUICK_TV_480P);
    public static final RegistryObject<BlockItem> QUICK_TV_720P = block("quick_tv_720p", CinemarrBlocks.QUICK_TV_720P);
    public static final RegistryObject<BlockItem> QUICK_TV_1080P = block("quick_tv_1080p", CinemarrBlocks.QUICK_TV_1080P);
    public static final RegistryObject<BlockItem> QUICK_TV_1440P = block("quick_tv_1440p", CinemarrBlocks.QUICK_TV_1440P);
    public static final RegistryObject<BlockItem> QUICK_TV_4K = block("quick_tv_4k", CinemarrBlocks.QUICK_TV_4K);
    public static final RegistryObject<BlockItem> QUICK_TV_8K = block("quick_tv_8k", CinemarrBlocks.QUICK_TV_8K);
    private static RegistryObject<BlockItem> block(String id, RegistryObject<? extends net.minecraft.world.level.block.Block> block) {
        return REGISTER.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }
    private CinemarrItems() {}
}
