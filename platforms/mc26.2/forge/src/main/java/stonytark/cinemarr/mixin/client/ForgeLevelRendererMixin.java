package stonytark.cinemarr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stonytark.cinemarr.client.CinemarrClient;

@Mixin(LevelRenderer.class)
public abstract class ForgeLevelRendererMixin {
    @Inject(method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void cinemarr$submitVideoGeometry(LevelRenderState state, SubmitNodeCollector submits,
                                              boolean renderOutline, CallbackInfo callback) {
        CinemarrClient.submitVideoGeometry(new PoseStack(), state.cameraRenderState.pos, submits);
    }
}
