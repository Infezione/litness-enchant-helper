package com.litness.optimizer.mixin;

import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

public interface ContainerScreenAccessor {
    void litness_invokeSlotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type);
}
