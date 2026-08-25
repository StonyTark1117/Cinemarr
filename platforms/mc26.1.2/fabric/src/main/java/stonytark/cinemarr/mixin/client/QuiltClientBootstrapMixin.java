package stonytark.cinemarr.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stonytark.cinemarr.client.CinemarrClient;
import stonytark.cinemarr.quilt.QuiltNetworkingCodecRepair;

@Mixin(Minecraft.class)
abstract class QuiltClientBootstrapMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cinemarr$bootstrapQuilt(CallbackInfo callback) {
        QuiltNetworkingCodecRepair.install();
        CinemarrClient.bootstrapQuilt();
    }
}
