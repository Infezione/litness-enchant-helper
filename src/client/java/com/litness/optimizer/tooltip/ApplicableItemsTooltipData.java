package com.litness.optimizer.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ApplicableItemsTooltipData(List<ItemStack> items, Component title) implements TooltipComponent {
}
