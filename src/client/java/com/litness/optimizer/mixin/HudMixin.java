package com.litness.optimizer.mixin;

import com.litness.optimizer.gui.LitnessHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void litness_renderPinnedHud(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            LitnessHudRenderer.render((Hud) (Object) this, extractor);
        } catch (Throwable ignored) {}
    }
}
