package stonytark.cinemarr.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrCreativeTabs {
    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(Cinemarr.MODID, "main"),
                FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.cinemarr"))
                    .icon(() -> new ItemStack(CinemarrItems.TV_REMOTE))
                    .displayItems((parameters, output) -> {
                        output.accept(CinemarrItems.SCREEN_PIXEL);
                        output.accept(CinemarrItems.TV_CONTROLLER);
                        output.accept(CinemarrItems.TV_CASING);
                        output.accept(CinemarrItems.TV_SPEAKER);
                        output.accept(CinemarrItems.REDSTONE_RECEIVER);
                        output.accept(CinemarrItems.TV_REMOTE);
                        output.accept(CinemarrItems.QUICK_TV_144P);
                        output.accept(CinemarrItems.QUICK_TV_240P);
                        output.accept(CinemarrItems.QUICK_TV_480P);
                        output.accept(CinemarrItems.QUICK_TV_720P);
                        output.accept(CinemarrItems.QUICK_TV_1080P);
                        output.accept(CinemarrItems.QUICK_TV_1440P);
                        output.accept(CinemarrItems.QUICK_TV_4K);
                        output.accept(CinemarrItems.QUICK_TV_8K);
                    }).build());
    }
    private CinemarrCreativeTabs() {}
}
