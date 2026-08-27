package stonytark.cinemarr.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, Cinemarr.MODID);
    public static final RegistryObject<BlockItem> SCREEN_PIXEL = REGISTER.register("screen_pixel", () -> new BlockItem(CinemarrBlocks.SCREEN_PIXEL.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> TV_CONTROLLER = REGISTER.register("tv_controller", () -> new BlockItem(CinemarrBlocks.TV_CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> TV_CASING = REGISTER.register("tv_casing", () -> new BlockItem(CinemarrBlocks.TV_CASING.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> TV_SPEAKER = REGISTER.register("tv_speaker", () -> new BlockItem(CinemarrBlocks.TV_SPEAKER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> REDSTONE_RECEIVER = REGISTER.register("redstone_receiver", () -> new BlockItem(CinemarrBlocks.REDSTONE_RECEIVER.get(), new Item.Properties()));
    public static final RegistryObject<Item> TV_REMOTE = REGISTER.register("tv_remote", () -> new Item(new Item.Properties().stacksTo(1)));
    public static Item tvRemote() { return TV_REMOTE.get(); }
    public static final RegistryObject<BlockItem> QUICK_TV_144P = quick("quick_tv_144p", CinemarrBlocks.QUICK_TV_144P);
    public static final RegistryObject<BlockItem> QUICK_TV_240P = quick("quick_tv_240p", CinemarrBlocks.QUICK_TV_240P);
    public static final RegistryObject<BlockItem> QUICK_TV_480P = quick("quick_tv_480p", CinemarrBlocks.QUICK_TV_480P);
    public static final RegistryObject<BlockItem> QUICK_TV_720P = quick("quick_tv_720p", CinemarrBlocks.QUICK_TV_720P);
    public static final RegistryObject<BlockItem> QUICK_TV_1080P = quick("quick_tv_1080p", CinemarrBlocks.QUICK_TV_1080P);
    public static final RegistryObject<BlockItem> QUICK_TV_1440P = quick("quick_tv_1440p", CinemarrBlocks.QUICK_TV_1440P);
    public static final RegistryObject<BlockItem> QUICK_TV_4K = quick("quick_tv_4k", CinemarrBlocks.QUICK_TV_4K);
    public static final RegistryObject<BlockItem> QUICK_TV_8K = quick("quick_tv_8k", CinemarrBlocks.QUICK_TV_8K);
    private static RegistryObject<BlockItem> quick(String id, RegistryObject<? extends net.minecraft.world.level.block.Block> block) { return REGISTER.register(id, () -> new BlockItem(block.get(), new Item.Properties())); }
    private CinemarrItems() {}
}
