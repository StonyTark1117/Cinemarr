package stonytark.cinemarr.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Cinemarr.MODID);
    public static final DeferredItem<BlockItem> SCREEN_PIXEL = register("screen_pixel", CinemarrBlocks.SCREEN_PIXEL);
    public static final DeferredItem<BlockItem> TV_CONTROLLER = register("tv_controller", CinemarrBlocks.TV_CONTROLLER);
    public static final DeferredItem<BlockItem> TV_CASING = register("tv_casing", CinemarrBlocks.TV_CASING);
    public static final DeferredItem<BlockItem> TV_SPEAKER = register("tv_speaker", CinemarrBlocks.TV_SPEAKER);
    public static final DeferredItem<BlockItem> REDSTONE_RECEIVER = register("redstone_receiver", CinemarrBlocks.REDSTONE_RECEIVER);
    public static final DeferredItem<Item> TV_REMOTE = item("tv_remote");
    public static Item tvRemote() { return TV_REMOTE.get(); }
    public static final DeferredItem<BlockItem> QUICK_TV_144P=register("quick_tv_144p",CinemarrBlocks.QUICK_TV_144P),QUICK_TV_240P=register("quick_tv_240p",CinemarrBlocks.QUICK_TV_240P),QUICK_TV_480P=register("quick_tv_480p",CinemarrBlocks.QUICK_TV_480P),QUICK_TV_720P=register("quick_tv_720p",CinemarrBlocks.QUICK_TV_720P),QUICK_TV_1080P=register("quick_tv_1080p",CinemarrBlocks.QUICK_TV_1080P),QUICK_TV_1440P=register("quick_tv_1440p",CinemarrBlocks.QUICK_TV_1440P),QUICK_TV_4K=register("quick_tv_4k",CinemarrBlocks.QUICK_TV_4K),QUICK_TV_8K=register("quick_tv_8k",CinemarrBlocks.QUICK_TV_8K);

    private static DeferredItem<BlockItem> register(String name,
            net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.Block,
                    ? extends net.minecraft.world.level.block.Block> block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Cinemarr.MODID, name));
        return REGISTER.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
    private static DeferredItem<Item> item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Cinemarr.MODID, name));
        return REGISTER.register(name, () -> new Item(new Item.Properties().stacksTo(1).setId(key)));
    }
    private CinemarrItems() {}
}
