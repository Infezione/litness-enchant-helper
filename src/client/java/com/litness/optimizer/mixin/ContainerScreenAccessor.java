package com.litness.optimizer.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Invoker("slotClicked")
    void litness_invokeSlotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type);
}
