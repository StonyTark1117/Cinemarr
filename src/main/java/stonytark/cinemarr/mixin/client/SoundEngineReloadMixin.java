package stonytark.cinemarr.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stonytark.cinemarr.client.CinemarrClient;

/** Resource-listener ordering cannot guarantee that old audio handles are still live. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void cinemarr$beforeSoundEngineReload(CallbackInfo callback) {
        CinemarrClient.soundEngineReloading();
    }
}
