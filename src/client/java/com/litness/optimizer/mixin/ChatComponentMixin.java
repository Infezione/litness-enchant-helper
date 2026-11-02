package com.litness.optimizer.mixin;

import com.litness.optimizer.gui.AhPriceManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void litness_onServerChatMessage(Component message, CallbackInfo ci) {
        if (message != null) {
            AhPriceManager.onChatMessage(message.getString());
        }
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void litness_onClientChatMessage(Component message, CallbackInfo ci) {
        if (message != null) {
            AhPriceManager.onChatMessage(message.getString());
        }
    }
}
