package stonytark.cinemarr.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import stonytark.cinemarr.Cinemarr;

public final class CinemarrCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cinemarr.MODID);
    public static final RegistryObject<CreativeModeTab> MAIN = REGISTER.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cinemarr"))
                    .icon(() -> new ItemStack(CinemarrItems.TV_REMOTE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(CinemarrItems.SCREEN_PIXEL.get());
                        output.accept(CinemarrItems.TV_CONTROLLER.get());
                        output.accept(CinemarrItems.TV_CASING.get());
                        output.accept(CinemarrItems.TV_SPEAKER.get());
                        output.accept(CinemarrItems.REDSTONE_RECEIVER.get());
                        output.accept(CinemarrItems.TV_REMOTE.get());
                        output.accept(CinemarrItems.QUICK_TV_144P.get());
                        output.accept(CinemarrItems.QUICK_TV_240P.get());
                        output.accept(CinemarrItems.QUICK_TV_480P.get());
                        output.accept(CinemarrItems.QUICK_TV_720P.get());
                        output.accept(CinemarrItems.QUICK_TV_1080P.get());
                        output.accept(CinemarrItems.QUICK_TV_1440P.get());
                        output.accept(CinemarrItems.QUICK_TV_4K.get());
                        output.accept(CinemarrItems.QUICK_TV_8K.get());
                    }).build());
    public static void register(IEventBus bus) { REGISTER.register(bus); }
    private CinemarrCreativeTabs() {}
}
