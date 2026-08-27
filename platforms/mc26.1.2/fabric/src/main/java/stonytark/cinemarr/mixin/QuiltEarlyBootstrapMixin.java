package stonytark.cinemarr.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stonytark.cinemarr.Cinemarr;

/** Invokes Fabric-compatible entrypoints before vanilla freezes intrusive block and item registries. */
@Mixin(value = BuiltInRegistries.class, priority = 1100)
abstract class QuiltEarlyBootstrapMixin {
    @Inject(method = "bootStrap", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/core/registries/BuiltInRegistries;freeze()V"))
    private static void cinemarr$bootstrapQuiltBeforeRegistryFreeze(CallbackInfo callback) {
        Cinemarr.bootstrapQuilt();
    }
}
